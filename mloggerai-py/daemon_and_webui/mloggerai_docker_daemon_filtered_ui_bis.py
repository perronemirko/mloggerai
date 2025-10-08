import argparse
import asyncio
import json
import re
import subprocess
from datetime import datetime
from typing import List, Dict, Any
from mloggerai.errorsolver import ErrorSolver

from fastapi import FastAPI, WebSocket
from fastapi.responses import HTMLResponse
import uvicorn

# --- PATTERN MULTI-LINGUAGGIO ---
LANGUAGE_PATTERNS = {
    "python": re.compile(r"(Traceback \(most recent call last\):|File \".+\.py\", line \d+|Exception|Error: .+)", re.IGNORECASE),
    "java": re.compile(r"(Exception in thread .+|at [\w\.]+\(.*\.java:\d+\)|Caused by: .+)", re.IGNORECASE),
    "rust": re.compile(r"(thread '.*' panicked at|RUST_BACKTRACE|error:\s.+|note:\s.+)", re.IGNORECASE),
    "javascript": re.compile(r"(TypeError:|ReferenceError:|SyntaxError:|UnhandledPromiseRejectionWarning|at .+\(.+:\d+:\d+\))", re.IGNORECASE),
    "kotlin": re.compile(r"(Exception in thread .+|at .+\.kt:\d+|Caused by: .+)", re.IGNORECASE),
}

ERROR_KEYWORDS = ["error", "exception", "failed", "panic", "fatal", "traceback", "segfault"]
EVENT_WINDOW_SECONDS = 1.5

# --- GLOBALI per la dashboard ---
active_clients: List[WebSocket] = []
recent_events: List[Dict[str, Any]] = []

# -------------------------------
# 🔍 CLASSE MONITOR DOCKER
# -------------------------------
class DockerMonitor:
    """Monitora i log di container Docker e invia errori a ErrorSolver."""

    def __init__(self, solver: ErrorSolver, json_log_path="logs/docker_events.jsonl", filter_pattern: str = None):
        self.solver = solver
        self.json_log_path = json_log_path
        self.filter_pattern = re.compile(filter_pattern, re.IGNORECASE) if filter_pattern else None
        self._buffers = {}
        self._last_times = {}
        self._active_tasks = {}
        self._ensure_dirs()

    def _ensure_dirs(self):
        import os
        os.makedirs("logs", exist_ok=True)

    def _detect_language(self, text: str) -> str:
        for lang, pattern in LANGUAGE_PATTERNS.items():
            if pattern.search(text):
                return lang
        return "generic"

    def _is_error_line(self, line: str) -> bool:
        l = line.lower()
        return any(k in l for k in ERROR_KEYWORDS) or any(p.search(line) for p in LANGUAGE_PATTERNS.values())

    def _matches_filter(self, container_name: str) -> bool:
        """Ritorna True se il container passa il filtro regex (o nessun filtro impostato)."""
        if not self.filter_pattern:
            return True
        return bool(self.filter_pattern.search(container_name))

    async def handle_line(self, container: str, line: str):
        now = datetime.now()
        line = line.rstrip("\n")
        if not line:
            return
        if self._is_error_line(line):
            last_time = self._last_times.get(container)
            if last_time is None or (now - last_time).total_seconds() > EVENT_WINDOW_SECONDS:
                self._buffers[container] = [line]
            else:
                self._buffers[container].append(line)
            self._last_times[container] = now
            await self._process_buffer(container)
        elif container in self._last_times and (now - self._last_times[container]).total_seconds() <= EVENT_WINDOW_SECONDS:
            self._buffers[container].append(line)
            self._last_times[container] = now

    async def _process_buffer(self, container: str):
        buf = self._buffers.get(container, [])
        if not buf:
            return
        text = "\n".join(buf)
        language = self._detect_language(text)
        timestamp = datetime.now().isoformat()

        try:
            solution = self.solver.solve_from_log(f"[{container}] {text}")
        except Exception as e:
            solution = f"Errore interno nel solver: {e}"

        event = {
            "timestamp": timestamp,
            "container": container,
            "language": language,
            "error": text,
            "solution": solution,
        }

        # dashboard buffer
        recent_events.append(event)
        if len(recent_events) > 100:
            recent_events.pop(0)

        await broadcast_json(event)

        # log file
        with open(self.json_log_path, "a", encoding="utf-8") as f:
            json.dump(event, f, ensure_ascii=False)
            f.write("\n")

        # self.solver.logger.info(f"[{container}] {language}\n{text}\n💡 {solution}\n---")
        self._buffers[container] = []
        self._last_times[container] = None

    async def attach_to_container(self, container_name: str):
        """Attacca il monitor solo se il nome passa il filtro."""
        if not self._matches_filter(container_name):
            self.solver.logger.info(f"⏩ Ignoro container '{container_name}' (non corrisponde al filtro)")
            return
        if container_name in self._active_tasks:
            return
        self.solver.logger.info(f"🔗 Attacco ai log di {container_name}")
        task = asyncio.create_task(self._read_container_logs(container_name))
        self._active_tasks[container_name] = task

    async def _read_container_logs(self, container_name: str):
        cmd = ["docker", "logs", "-f", container_name]
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )

        async def read_stream(stream):
            while True:
                line = await stream.readline()
                if not line:
                    break
                await self.handle_line(container_name, line.decode(errors="ignore"))

        await asyncio.gather(read_stream(proc.stdout), read_stream(proc.stderr))
        self._active_tasks.pop(container_name, None)
        self.solver.logger.info(f"📴 Container {container_name} terminato")

    async def monitor_existing_containers(self):
        proc = subprocess.run(["docker", "ps", "--format", "{{.Names}}"], capture_output=True, text=True)
        containers = [c.strip() for c in proc.stdout.splitlines() if c.strip()]
        for c in containers:
            await self.attach_to_container(c)

    async def listen_for_new_containers(self):
        self.solver.logger.info("🎧 Ascolto eventi Docker...")
        proc = await asyncio.create_subprocess_exec(
            "docker", "events", "--format", "{{.Status}} {{.Actor.Attributes.name}}",
            stdout=asyncio.subprocess.PIPE,
        )
        async for line_bytes in proc.stdout:
            line = line_bytes.decode(errors="ignore").strip()
            if not line:
                continue
            try:
                status, name = line.split(" ", 1)
            except ValueError:
                continue
            if status == "start":
                self.solver.logger.info(f"🆕 Nuovo container: {name}")
                await self.attach_to_container(name)
            elif status == "die":
                self.solver.logger.info(f"💀 Container terminato: {name}")


# -------------------------------
# 🌐 DASHBOARD FASTAPI
# -------------------------------
app = FastAPI(title="ErrorSolver Docker Monitor")

html1 = """
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ErrorSolver Docker Dashboard</title>

    <!-- Bootstrap CSS -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">

    <!-- Material Icons -->
    <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">

    <style>
        /* General Styles */
        body {
            background: #1b1b1b;
            color: #ddd;
            font-family: 'Roboto', sans-serif;
            display: flex;
            flex-direction: column;
            min-height: 100vh;
            margin: 0;
            overflow: hidden;
        }
        #controls {
            padding: 1rem;
            background: #212121;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid #444;
        }
        #log {
            flex-grow: 1;
            overflow-y: auto;
            padding: 1rem;
            max-height: calc(100vh - 90px); /* Altezza fissa meno la barra di controllo */
            margin-top: 10px;
        }
        .card {
            background: #333;
            margin-block-end: 1rem;
            border-radius: 8px;
            border: 1px solid #444;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
            transition: transform 0.2s ease, opacity 0.2s ease;
            cursor: pointer;
        }
        .card:hover {
            transform: translateY(-5px);
            opacity: 0.9;
        }
        .card-header {
            font-weight: bold;
            color: #00ffcc;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .card-body {
            white-space: pre-wrap;
            font-family: 'Courier New', Courier, monospace;
            line-height: 1.5;
        }
        .error {
            color: #ff4444;
        }
        .solution {
            color: #88ff88;
            margin-top: 10px;
        }
        .lang {
            color: #ffaa00;
            margin-inline-start: 0.5rem;
        }

        /* Button styles */
        .btn-custom {
            background: #00ffcc;
            color: #111;
            border: none;
            transition: background-color 0.3s;
        }
        .btn-custom:hover {
            background-color: #00cc99;
        }

        .badge-custom {
            font-size: 1rem;
            padding: 0.5rem 1rem;
            border-radius: 20px;
            transition: background-color 0.3s;
        }
        .badge-custom.online {
            background-color: #28a745;
        }
        .badge-custom.offline {
            background-color: #dc3545;
        }

        /* Modale Styles */
        .modal-content {
            background-color: #2c2c2c;
            border-radius: 10px;
            padding: 20px;
            border: none;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
        }
        .modal-header {
            border-bottom: 1px solid #444;
            background-color: #1a1a1a;
            color: #fff;
        }
        .modal-header .btn-close {
            color: #00ffcc;
        }
        .modal-title {
            font-size: 1.5rem;
        }
        .modal-body {
            color: #ddd;
            font-size: 1rem;
            line-height: 1.6;
        }
        .modal-body h5 {
            font-size: 1.25rem;
            color: #00ffcc;
        }
        .modal-body .error {
            color: #ff4444;
        }
        .modal-body .solution {
            color: #88ff88;
            font-weight: bold;
        }

        /* Smooth transitions */
        .card-body div {
            opacity: 0;
            animation: fadeIn 0.5s forwards;
        }
        @keyframes fadeIn {
            from {
                opacity: 0;
            }
            to {
                opacity: 1;
            }
        }
    </style>
</head>
<body>
<div id="controls">
    <button id="clear" class="btn btn-custom"><span class="material-icons">delete</span> Clear</button>
    <span id="status" class="badge badge-custom offline">Offline</span>
</div>
<div id="log" class="container-fluid"></div>

<!-- Modale per visualizzare i dettagli dell'errore -->
<div class="modal fade" id="errorModal" tabindex="-1" aria-labelledby="errorModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="errorModalLabel">Dettaglio Errore</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <h5 id="modalContainerName"></h5>
                <pre id="modalErrorDetail" class="error"></pre>
                <div id="modalSolution" class="solution"></div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Chiudi</button>
            </div>
        </div>
    </div>
</div>

<script>
    const logDiv = document.getElementById("log");
    const statusBadge = document.getElementById("status");
    const ws = new WebSocket(`ws://${location.host}/ws`);

    // Modale elementi
    const modalContainerName = document.getElementById("modalContainerName");
    const modalErrorDetail = document.getElementById("modalErrorDetail");
    const modalSolution = document.getElementById("modalSolution");

    ws.onopen = () => {
        statusBadge.textContent = "Online";
        statusBadge.className = "badge badge-custom online";
    };
    ws.onclose = () => {
        statusBadge.textContent = "Offline";
        statusBadge.className = "badge badge-custom offline";
    };

    ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        
        const card = document.createElement("div");
        card.className = "card";
        
        const header = document.createElement("div");
        header.className = "card-header";
        header.innerHTML = `<span>[${data.container}]</span> <span class="lang">(${data.language})</span>`;
        
        const body = document.createElement("div");
        body.className = "card-body";
        body.innerHTML = `
            <div class="error">${data.error}</div>
            <div class="solution">💡 ${data.solution}</div>
        `;
        
        card.appendChild(header);
        card.appendChild(body);

        // Aggiungi l'evento di click sulla card per aprire la modale
        card.addEventListener("click", () => {
            // Popola la modale con il dettaglio dell'errore
            modalContainerName.textContent = `[${data.container}] (${data.language})`;
            modalErrorDetail.textContent = data.error;
            modalSolution.textContent = `💡 ${data.solution}`;

            // Mostra la modale
            const modal = new bootstrap.Modal(document.getElementById('errorModal'));
            modal.show();
        });

        logDiv.prepend(card); // Prepend for most recent events on top
        
        // Scroll to bottom after adding a new card
        logDiv.scrollTop = logDiv.scrollHeight;
    };

    document.getElementById("clear").onclick = () => {
        logDiv.innerHTML = '';
    };
</script>

<!-- Bootstrap and Material Icons JS -->
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>

"""
html2 = """
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ErrorSolver Docker Dashboard</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
    <style>
        @keyframes slideIn {
            from {
                opacity: 0;
                transform: translateY(-20px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }
        @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }
        .animate-slide-in {
            animation: slideIn 0.4s ease-out;
        }
        .animate-fade-in {
            animation: fadeIn 0.3s ease-in;
        }
        .glass-effect {
            backdrop-filter: blur(10px);
            background: rgba(255, 255, 255, 0.05);
        }
        .dark .glass-effect {
            background: rgba(0, 0, 0, 0.3);
        }
        .error-card {
            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .error-card:hover {
            transform: translateY(-8px) scale(1.02);
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
        }
        .scrollbar-hide::-webkit-scrollbar {
            display: none;
        }
        .scrollbar-hide {
            -ms-overflow-style: none;
            scrollbar-width: none;
        }
        .gradient-border {
            position: relative;
            background: linear-gradient(145deg, rgba(34, 211, 238, 0.1), rgba(168, 85, 247, 0.1));
        }
    </style>
</head>
<body class="bg-gradient-to-br from-slate-50 to-slate-100 dark:from-gray-900 dark:to-gray-950 text-gray-900 dark:text-gray-100 transition-colors duration-300">
    
    <!-- Header con controlli -->
    <header class="fixed top-0 left-0 right-0 z-50 glass-effect border-b border-gray-200/20 dark:border-gray-700/20">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div class="flex items-center justify-between h-16">
                <!-- Logo e titolo -->
                <div class="flex items-center space-x-3">
                    <div class="w-10 h-10 bg-gradient-to-br from-cyan-400 to-purple-500 rounded-lg flex items-center justify-center shadow-lg">
                        <span class="material-icons text-white text-2xl">bug_report</span>
                    </div>
                    <div>
                        <h1 class="text-xl font-bold bg-gradient-to-r from-cyan-500 to-purple-500 bg-clip-text text-transparent">ErrorSolver</h1>
                        <p class="text-xs text-gray-500 dark:text-gray-400">Docker Dashboard</p>
                    </div>
                </div>

                <!-- Controlli -->
                <div class="flex items-center space-x-4">
                    <!-- Status Badge -->
                    <div id="status" class="flex items-center space-x-2 px-4 py-2 rounded-full bg-red-500/10 border border-red-500/30">
                        <span class="w-2 h-2 rounded-full bg-red-500 animate-pulse"></span>
                        <span class="text-sm font-medium text-red-500">Offline</span>
                    </div>

                    <!-- Dark Mode Toggle -->
                    <button id="themeToggle" class="p-2 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-800 transition-colors">
                        <span class="material-icons text-gray-700 dark:text-gray-300">dark_mode</span>
                    </button>

                    <!-- Clear Button -->
                    <button id="clear" class="flex items-center space-x-2 px-4 py-2 bg-gradient-to-r from-cyan-500 to-purple-500 hover:from-cyan-600 hover:to-purple-600 text-white rounded-lg shadow-lg transition-all duration-300 hover:scale-105">
                        <span class="material-icons text-sm">delete_sweep</span>
                        <span class="font-medium">Clear</span>
                    </button>
                </div>
            </div>
        </div>
    </header>

    <!-- Main Content -->
    <main class="pt-24 pb-8 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto">
        <div id="log" class="space-y-4 scrollbar-hide" style="max-height: calc(100vh - 120px); overflow-y: auto;">
            <!-- Le card degli errori verranno inserite qui -->
        </div>

        <!-- Empty State -->
        <div id="emptyState" class="flex flex-col items-center justify-center h-96 animate-fade-in">
            <div class="w-24 h-24 bg-gradient-to-br from-cyan-400/20 to-purple-500/20 rounded-full flex items-center justify-center mb-6">
                <span class="material-icons text-6xl text-gray-400 dark:text-gray-600">inbox</span>
            </div>
            <h3 class="text-2xl font-bold text-gray-700 dark:text-gray-300 mb-2">Nessun Errore</h3>
            <p class="text-gray-500 dark:text-gray-400 text-center max-w-md">
                Il sistema è in attesa di errori dai container Docker. Gli errori appariranno qui in tempo reale.
            </p>
        </div>
    </main>

    <!-- Modal -->
    <div id="errorModal" class="fixed inset-0 z-50 hidden items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
        <div class="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl max-w-3xl w-full max-h-[90vh] overflow-hidden animate-slide-in border border-gray-200 dark:border-gray-700">
            <!-- Modal Header -->
            <div class="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700 bg-gradient-to-r from-cyan-500/10 to-purple-500/10">
                <div>
                    <h2 class="text-2xl font-bold text-gray-900 dark:text-white">Dettaglio Errore</h2>
                    <p id="modalContainerName" class="text-sm text-gray-600 dark:text-gray-400 mt-1"></p>
                </div>
                <button id="closeModal" class="p-2 hover:bg-gray-200 dark:hover:bg-gray-800 rounded-lg transition-colors">
                    <span class="material-icons text-gray-600 dark:text-gray-400">close</span>
                </button>
            </div>

            <!-- Modal Body -->
            <div class="p-6 overflow-y-auto" style="max-height: calc(90vh - 180px);">
                <div class="mb-6">
                    <h3 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-3">Errore</h3>
                    <div class="bg-red-500/5 border-l-4 border-red-500 p-4 rounded-r-lg">
                        <pre id="modalErrorDetail" class="text-red-600 dark:text-red-400 whitespace-pre-wrap text-sm font-mono"></pre>
                    </div>
                </div>

                <div>
                    <h3 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-3">Soluzione Suggerita</h3>
                    <div class="bg-green-500/5 border-l-4 border-green-500 p-4 rounded-r-lg">
                        <div class="flex items-start space-x-3">
                            <span class="material-icons text-green-500 text-2xl">lightbulb</span>
                            <p id="modalSolution" class="text-green-600 dark:text-green-400 text-sm leading-relaxed"></p>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Modal Footer -->
            <div class="flex justify-end p-6 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
                <button id="closeModalBtn" class="px-6 py-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 rounded-lg transition-colors font-medium">
                    Chiudi
                </button>
            </div>
        </div>
    </div>

    <script>
        const logDiv = document.getElementById("log");
        const statusDiv = document.getElementById("status");
        const emptyState = document.getElementById("emptyState");
        const modal = document.getElementById("errorModal");
        const themeToggle = document.getElementById("themeToggle");
        
        // Theme management
        const theme = {
            current: localStorage.getItem('theme') || 'light',
            toggle() {
                this.current = this.current === 'dark' ? 'light' : 'dark';
                this.apply();
            },
            apply() {
                if (this.current === 'dark') {
                    document.documentElement.classList.add('dark');
                    themeToggle.querySelector('.material-icons').textContent = 'light_mode';
                } else {
                    document.documentElement.classList.remove('dark');
                    themeToggle.querySelector('.material-icons').textContent = 'dark_mode';
                }
                localStorage.setItem('theme', this.current);
            }
        };
        theme.apply();

        themeToggle.addEventListener('click', () => theme.toggle());

        // WebSocket connection
        const ws = new WebSocket(`ws://${location.host}/ws`);

        ws.onopen = () => {
            statusDiv.innerHTML = `
                <span class="w-2 h-2 rounded-full bg-green-500 animate-pulse"></span>
                <span class="text-sm font-medium text-green-500">Online</span>
            `;
            statusDiv.className = "flex items-center space-x-2 px-4 py-2 rounded-full bg-green-500/10 border border-green-500/30";
        };

        ws.onclose = () => {
            statusDiv.innerHTML = `
                <span class="w-2 h-2 rounded-full bg-red-500 animate-pulse"></span>
                <span class="text-sm font-medium text-red-500">Offline</span>
            `;
            statusDiv.className = "flex items-center space-x-2 px-4 py-2 rounded-full bg-red-500/10 border border-red-500/30";
        };

        ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            
            emptyState.classList.add('hidden');
            
            const card = document.createElement("div");
            card.className = "error-card gradient-border p-6 rounded-xl shadow-lg hover:shadow-2xl cursor-pointer border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 animate-slide-in";
            
            const languageColors = {
                'python': 'from-blue-400 to-blue-600',
                'javascript': 'from-yellow-400 to-yellow-600',
                'java': 'from-red-400 to-red-600',
                'default': 'from-purple-400 to-purple-600'
            };
            
            const colorClass = languageColors[data.language?.toLowerCase()] || languageColors.default;
            
            card.innerHTML = `
                <div class="flex items-start justify-between mb-4">
                    <div class="flex items-center space-x-3">
                        <div class="w-12 h-12 bg-gradient-to-br ${colorClass} rounded-lg flex items-center justify-center shadow-lg">
                            <span class="material-icons text-white">terminal</span>
                        </div>
                        <div>
                            <h3 class="font-bold text-lg text-gray-900 dark:text-white">${data.container}</h3>
                            <span class="inline-block px-3 py-1 text-xs font-semibold bg-gradient-to-r ${colorClass} text-white rounded-full mt-1">
                                ${data.language}
                            </span>
                        </div>
                    </div>
                    <span class="text-xs text-gray-500 dark:text-gray-400">${new Date().toLocaleTimeString('it-IT')}</span>
                </div>
                
                <div class="space-y-3">
                    <div class="bg-red-500/5 border-l-4 border-red-500 p-3 rounded-r">
                        <p class="text-sm text-red-600 dark:text-red-400 font-mono line-clamp-2">${data.error}</p>
                    </div>
                    
                    <div class="bg-green-500/5 border-l-4 border-green-500 p-3 rounded-r">
                        <div class="flex items-start space-x-2">
                            <span class="material-icons text-green-500 text-lg mt-0.5">lightbulb</span>
                            <p class="text-sm text-green-600 dark:text-green-400 line-clamp-2">${data.solution}</p>
                        </div>
                    </div>
                </div>
            `;
            
            card.addEventListener("click", () => {
                document.getElementById("modalContainerName").textContent = `${data.container} (${data.language})`;
                document.getElementById("modalErrorDetail").textContent = data.error;
                document.getElementById("modalSolution").textContent = data.solution;
                modal.classList.remove('hidden');
                modal.classList.add('flex');
            });

            logDiv.prepend(card);
        };

        // Modal controls
        document.getElementById("closeModal").addEventListener("click", () => {
            modal.classList.add('hidden');
            modal.classList.remove('flex');
        });
        
        document.getElementById("closeModalBtn").addEventListener("click", () => {
            modal.classList.add('hidden');
            modal.classList.remove('flex');
        });
        
        modal.addEventListener("click", (e) => {
            if (e.target === modal) {
                modal.classList.add('hidden');
                modal.classList.remove('flex');
            }
        });

        // Clear logs
        document.getElementById("clear").addEventListener("click", () => {
            logDiv.innerHTML = '';
            emptyState.classList.remove('hidden');
        });
    </script>
</body>
</html>
"""
html ="""<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ErrorSolver Docker Dashboard</title>
    <script>
        // Prevent flash of unstyled content - apply dark mode immediately
        (function() {
            const theme = localStorage.getItem('theme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
            if (theme === 'dark') {
                document.documentElement.classList.add('dark');
            }
        })();
    </script>
    <script src="https://cdn.tailwindcss.com"></script>
    <script>
        tailwind.config = {
            darkMode: 'class',
        }
    </script>
    <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
    <style>
        @keyframes slideIn {
            from {
                opacity: 0;
                transform: translateY(-20px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }
        @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }
        .animate-slide-in {
            animation: slideIn 0.4s ease-out;
        }
        .animate-fade-in {
            animation: fadeIn 0.3s ease-in;
        }
        .glass-effect {
            backdrop-filter: blur(10px);
            background: rgba(255, 255, 255, 0.8);
            border: 1px solid rgba(255, 255, 255, 0.2);
        }
        .dark .glass-effect {
            background: rgba(0, 0, 0, 0.4);
            border: 1px solid rgba(255, 255, 255, 0.1);
        }
        .error-card {
            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .error-card:hover {
            transform: translateY(-8px) scale(1.02);
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
        }
        .scrollbar-hide::-webkit-scrollbar {
            display: none;
        }
        .scrollbar-hide {
            -ms-overflow-style: none;
            scrollbar-width: none;
        }
        * {
            transition: background-color 0.3s ease, color 0.3s ease, border-color 0.3s ease;
        }
    </style>
</head>
<body class="bg-gradient-to-br from-slate-50 via-blue-50 to-purple-50 dark:from-gray-950 dark:via-gray-900 dark:to-slate-950 text-gray-900 dark:text-gray-100 min-h-screen">
    
    <!-- Header con controlli -->
    <header class="fixed top-0 left-0 right-0 z-50 glass-effect shadow-lg">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div class="flex items-center justify-between h-16">
                <!-- Logo e titolo -->
                <div class="flex items-center space-x-3">
                    <div class="w-10 h-10 bg-gradient-to-br from-cyan-500 to-purple-600 rounded-lg flex items-center justify-center shadow-lg">
                        <span class="material-icons text-white text-2xl">bug_report</span>
                    </div>
                    <div>
                        <h1 class="text-xl font-bold bg-gradient-to-r from-cyan-600 to-purple-600 bg-clip-text text-transparent">ErrorSolver</h1>
                        <p class="text-xs text-gray-600 dark:text-gray-400">Docker Dashboard</p>
                    </div>
                </div>

                <!-- Controlli -->
                <div class="flex items-center space-x-4">
                    <!-- Status Badge -->
                    <div id="status" class="flex items-center space-x-2 px-4 py-2 rounded-full bg-red-100 dark:bg-red-950 border border-red-300 dark:border-red-800">
                        <span class="w-2 h-2 rounded-full bg-red-500 animate-pulse"></span>
                        <span class="text-sm font-medium text-red-600 dark:text-red-400">Offline</span>
                    </div>

                    <!-- Dark Mode Toggle -->
                    <button id="themeToggle" class="p-2 rounded-lg bg-gray-200 dark:bg-gray-800 hover:bg-gray-300 dark:hover:bg-gray-700 transition-all shadow-md">
                        <span class="material-icons text-gray-800 dark:text-gray-200">dark_mode</span>
                    </button>

                    <!-- Clear Button -->
                    <button id="clear" class="flex items-center space-x-2 px-4 py-2 bg-gradient-to-r from-cyan-500 to-purple-600 hover:from-cyan-600 hover:to-purple-700 text-white rounded-lg shadow-lg transition-all duration-300 hover:scale-105">
                        <span class="material-icons text-sm">delete_sweep</span>
                        <span class="font-medium">Clear</span>
                    </button>
                </div>
            </div>
        </div>
    </header>

    <!-- Main Content -->
    <main class="pt-24 pb-8 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto">
        <div id="log" class="space-y-4 scrollbar-hide" style="max-height: calc(100vh - 120px); overflow-y: auto;">
            <!-- Le card degli errori verranno inserite qui -->
        </div>

        <!-- Empty State -->
        <div id="emptyState" class="flex flex-col items-center justify-center h-96 animate-fade-in">
            <div class="w-24 h-24 bg-gradient-to-br from-cyan-100 to-purple-100 dark:from-cyan-950 dark:to-purple-950 rounded-full flex items-center justify-center mb-6 shadow-lg">
                <span class="material-icons text-6xl text-gray-400 dark:text-gray-600">inbox</span>
            </div>
            <h3 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">Nessun Errore</h3>
            <p class="text-gray-600 dark:text-gray-400 text-center max-w-md">
                Il sistema è in attesa di errori dai container Docker. Gli errori appariranno qui in tempo reale.
            </p>
        </div>
    </main>

    <!-- Modal -->
    <div id="errorModal" class="fixed inset-0 z-50 hidden items-center justify-center p-4 bg-black/60 dark:bg-black/80 backdrop-blur-sm">
        <div class="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl max-w-3xl w-full max-h-[90vh] overflow-hidden animate-slide-in border border-gray-300 dark:border-gray-700">
            <!-- Modal Header -->
            <div class="flex items-center justify-between p-6 border-b border-gray-300 dark:border-gray-700 bg-gradient-to-r from-cyan-50 to-purple-50 dark:from-cyan-950 dark:to-purple-950">
                <div>
                    <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100">Dettaglio Errore</h2>
                    <p id="modalContainerName" class="text-sm text-gray-600 dark:text-gray-400 mt-1"></p>
                </div>
                <button id="closeModal" class="p-2 hover:bg-gray-200 dark:hover:bg-gray-800 rounded-lg transition-colors">
                    <span class="material-icons text-gray-700 dark:text-gray-300">close</span>
                </button>
            </div>

            <!-- Modal Body -->
            <div class="p-6 overflow-y-auto bg-white dark:bg-gray-900" style="max-height: calc(90vh - 180px);">
                <div class="mb-6">
                    <h3 class="text-sm font-semibold text-gray-600 dark:text-gray-400 uppercase tracking-wider mb-3">Errore</h3>
                    <div class="bg-red-50 dark:bg-red-950 border-l-4 border-red-500 p-4 rounded-r-lg">
                        <pre id="modalErrorDetail" class="text-red-700 dark:text-red-300 whitespace-pre-wrap text-sm font-mono"></pre>
                    </div>
                </div>

                <div>
                    <h3 class="text-sm font-semibold text-gray-600 dark:text-gray-400 uppercase tracking-wider mb-3">Soluzione Suggerita</h3>
                    <div class="bg-green-50 dark:bg-green-950 border-l-4 border-green-500 p-4 rounded-r-lg">
                        <div class="flex items-start space-x-3">
                            <span class="material-icons text-green-600 dark:text-green-400 text-2xl">lightbulb</span>
                            <p id="modalSolution" class="text-green-700 dark:text-green-300 text-sm leading-relaxed"></p>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Modal Footer -->
            <div class="flex justify-end p-6 border-t border-gray-300 dark:border-gray-700 bg-gray-50 dark:bg-gray-800">
                <button id="closeModalBtn" class="px-6 py-2 bg-gray-300 dark:bg-gray-700 hover:bg-gray-400 dark:hover:bg-gray-600 text-gray-800 dark:text-gray-200 rounded-lg transition-colors font-medium">
                    Chiudi
                </button>
            </div>
        </div>
    </div>

    <script>
        const logDiv = document.getElementById("log");
        const statusDiv = document.getElementById("status");
        const emptyState = document.getElementById("emptyState");
        const modal = document.getElementById("errorModal");
        const themeToggle = document.getElementById("themeToggle");
        
        // Theme management
        const theme = {
            current: localStorage.getItem('theme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'),
            toggle() {
                this.current = this.current === 'dark' ? 'light' : 'dark';
                this.apply();
            },
            apply() {
                if (this.current === 'dark') {
                    document.documentElement.classList.add('dark');
                    themeToggle.querySelector('.material-icons').textContent = 'light_mode';
                } else {
                    document.documentElement.classList.remove('dark');
                    themeToggle.querySelector('.material-icons').textContent = 'dark_mode';
                }
                localStorage.setItem('theme', this.current);
            }
        };
        
        // Apply theme immediately
        theme.apply();

        themeToggle.addEventListener('click', () => theme.toggle());

        // WebSocket connection
        const ws = new WebSocket(`ws://${location.host}/ws`);

        ws.onopen = () => {
            statusDiv.innerHTML = `
                <span class="w-2 h-2 rounded-full bg-green-500 animate-pulse"></span>
                <span class="text-sm font-medium text-green-600 dark:text-green-400">Online</span>
            `;
            statusDiv.className = "flex items-center space-x-2 px-4 py-2 rounded-full bg-green-100 dark:bg-green-950 border border-green-300 dark:border-green-800";
        };

        ws.onclose = () => {
            statusDiv.innerHTML = `
                <span class="w-2 h-2 rounded-full bg-red-500 animate-pulse"></span>
                <span class="text-sm font-medium text-red-600 dark:text-red-400">Offline</span>
            `;
            statusDiv.className = "flex items-center space-x-2 px-4 py-2 rounded-full bg-red-100 dark:bg-red-950 border border-red-300 dark:border-red-800";
        };

        ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            
            emptyState.classList.add('hidden');
            
            const card = document.createElement("div");
            card.className = "error-card p-6 rounded-xl shadow-lg hover:shadow-2xl cursor-pointer border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 animate-slide-in";
            
            const languageColors = {
                'python': { light: 'from-blue-400 to-blue-600', dark: 'from-blue-500 to-blue-700' },
                'javascript': { light: 'from-yellow-400 to-yellow-600', dark: 'from-yellow-500 to-yellow-700' },
                'java': { light: 'from-red-400 to-red-600', dark: 'from-red-500 to-red-700' },
                'default': { light: 'from-purple-400 to-purple-600', dark: 'from-purple-500 to-purple-700' }
            };
            
            const colorClass = languageColors[data.language?.toLowerCase()] || languageColors.default;
            
            card.innerHTML = `
                <div class="flex items-start justify-between mb-4">
                    <div class="flex items-center space-x-3">
                        <div class="w-12 h-12 bg-gradient-to-br ${colorClass.light} rounded-lg flex items-center justify-center shadow-lg">
                            <span class="material-icons text-white">terminal</span>
                        </div>
                        <div>
                            <h3 class="font-bold text-lg text-gray-900 dark:text-gray-100">${data.container}</h3>
                            <span class="inline-block px-3 py-1 text-xs font-semibold bg-gradient-to-r ${colorClass.light} text-white rounded-full mt-1">
                                ${data.language}
                            </span>
                        </div>
                    </div>
                    <span class="text-xs text-gray-500 dark:text-gray-400">${new Date().toLocaleTimeString('it-IT')}</span>
                </div>
                
                <div class="space-y-3">
                    <div class="bg-red-50 dark:bg-red-950 border-l-4 border-red-500 p-3 rounded-r">
                        <p class="text-sm text-red-700 dark:text-red-300 font-mono line-clamp-2">${data.error}</p>
                    </div>
                    
                    <div class="bg-green-50 dark:bg-green-950 border-l-4 border-green-500 p-3 rounded-r">
                        <div class="flex items-start space-x-2">
                            <span class="material-icons text-green-600 dark:text-green-400 text-lg mt-0.5">lightbulb</span>
                            <p class="text-sm text-green-700 dark:text-green-300 line-clamp-2">${data.solution}</p>
                        </div>
                    </div>
                </div>
            `;
            
            card.addEventListener("click", () => {
                document.getElementById("modalContainerName").textContent = `${data.container} (${data.language})`;
                document.getElementById("modalErrorDetail").textContent = data.error;
                document.getElementById("modalSolution").textContent = data.solution;
                modal.classList.remove('hidden');
                modal.classList.add('flex');
            });

            logDiv.prepend(card);
        };

        // Modal controls
        document.getElementById("closeModal").addEventListener("click", () => {
            modal.classList.add('hidden');
            modal.classList.remove('flex');
        });
        
        document.getElementById("closeModalBtn").addEventListener("click", () => {
            modal.classList.add('hidden');
            modal.classList.remove('flex');
        });
        
        modal.addEventListener("click", (e) => {
            if (e.target === modal) {
                modal.classList.add('hidden');
                modal.classList.remove('flex');
            }
        });

        // Clear logs
        document.getElementById("clear").addEventListener("click", () => {
            logDiv.innerHTML = '';
            emptyState.classList.remove('hidden');
        });
    </script>
</body>
</html>"""


html="""
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ErrorSolver Docker Dashboard</title>
    <script>
        // Prevent flash of unstyled content - apply dark mode immediately
        (function() {
            const theme = localStorage.getItem('theme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
            if (theme === 'dark') {
                document.documentElement.classList.add('dark');
            }
        })();
    </script>
    <script src="https://cdn.tailwindcss.com"></script>
    <script>
        tailwind.config = {
            darkMode: 'class',
        }
    </script>
    <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
    <style>
        @keyframes slideIn {
            from {
                opacity: 0;
                transform: translateY(-20px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }
        @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }
        .animate-slide-in {
            animation: slideIn 0.4s ease-out;
        }
        .animate-fade-in {
            animation: fadeIn 0.3s ease-in;
        }
        .glass-effect {
            backdrop-filter: blur(10px);
            background: rgba(255, 255, 255, 0.8);
            border: 1px solid rgba(255, 255, 255, 0.2);
        }
        .dark .glass-effect {
            background: rgba(0, 0, 0, 0.4);
            border: 1px solid rgba(255, 255, 255, 0.1);
        }
        .error-card {
            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .error-card:hover {
            transform: translateY(-8px) scale(1.02);
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
        }
        .scrollbar-hide::-webkit-scrollbar {
            display: none;
        }
        .scrollbar-hide {
            -ms-overflow-style: none;
            scrollbar-width: none;
        }
        * {
            transition: background-color 0.3s ease, color 0.3s ease, border-color 0.3s ease;
        }
    </style>
</head>
<body class="bg-gradient-to-br from-slate-50 via-blue-50 to-purple-50 dark:from-gray-950 dark:via-gray-900 dark:to-slate-950 text-gray-900 dark:text-gray-100 min-h-screen">
    
    <!-- Header con controlli -->
    <header class="fixed top-0 left-0 right-0 z-50 glass-effect shadow-lg">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div class="flex items-center justify-between h-16">
                <!-- Logo e titolo -->
                <div class="flex items-center space-x-3">
                    <div class="w-10 h-10 bg-gradient-to-br from-cyan-500 to-purple-600 rounded-lg flex items-center justify-center shadow-lg">
                        <span class="material-icons text-white text-2xl">bug_report</span>
                    </div>
                    <div>
                        <h1 class="text-xl font-bold bg-gradient-to-r from-cyan-600 to-purple-600 bg-clip-text text-transparent">ErrorSolver</h1>
                        <p class="text-xs text-gray-600 dark:text-gray-400">Docker Dashboard</p>
                    </div>
                </div>

                <!-- Controlli -->
                <div class="flex items-center space-x-4">
                    <!-- Status Badge -->
                    <div id="status" class="flex items-center space-x-2 px-4 py-2 rounded-full bg-red-100 dark:bg-red-950 border border-red-300 dark:border-red-800">
                        <span class="w-2 h-2 rounded-full bg-red-500 animate-pulse"></span>
                        <span class="text-sm font-medium text-red-600 dark:text-red-400">Offline</span>
                    </div>

                    <!-- Dark Mode Toggle -->
                    <button id="themeToggle" class="p-2 rounded-lg bg-gray-200 dark:bg-gray-800 hover:bg-gray-300 dark:hover:bg-gray-700 transition-all shadow-md">
                        <span class="material-icons text-gray-800 dark:text-gray-200">dark_mode</span>
                    </button>

                    <!-- Clear Button -->
                    <button id="clear" class="flex items-center space-x-2 px-4 py-2 bg-gradient-to-r from-cyan-500 to-purple-600 hover:from-cyan-600 hover:to-purple-700 text-white rounded-lg shadow-lg transition-all duration-300 hover:scale-105">
                        <span class="material-icons text-sm">delete_sweep</span>
                        <span class="font-medium">Clear</span>
                    </button>
                </div>
            </div>
        </div>
    </header>

    <!-- Main Content -->
    <main class="pt-24 pb-8 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto">
        <div id="log" class="space-y-4 scrollbar-hide" style="max-height: calc(100vh - 120px); overflow-y: auto;">
            <!-- Le card degli errori verranno inserite qui -->
        </div>

        <!-- Empty State -->
        <div id="emptyState" class="flex flex-col items-center justify-center h-96 animate-fade-in">
            <div class="w-24 h-24 bg-gradient-to-br from-cyan-100 to-purple-100 dark:from-cyan-950 dark:to-purple-950 rounded-full flex items-center justify-center mb-6 shadow-lg">
                <span class="material-icons text-6xl text-gray-400 dark:text-gray-600">inbox</span>
            </div>
            <h3 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">Nessun Errore</h3>
            <p class="text-gray-600 dark:text-gray-400 text-center max-w-md">
                Il sistema è in attesa di errori dai container Docker. Gli errori appariranno qui in tempo reale.
            </p>
        </div>
    </main>

    <!-- Modal -->
    <div id="errorModal" class="fixed inset-0 z-50 hidden items-center justify-center p-4 bg-black/60 dark:bg-black/80 backdrop-blur-sm">
        <div class="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl max-w-3xl w-full max-h-[90vh] overflow-hidden animate-slide-in border border-gray-300 dark:border-gray-700">
            <!-- Modal Header -->
            <div class="flex items-center justify-between p-6 border-b border-gray-300 dark:border-gray-700 bg-gradient-to-r from-cyan-50 to-purple-50 dark:from-cyan-950 dark:to-purple-950">
                <div>
                    <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100">Dettaglio Errore</h2>
                    <p id="modalContainerName" class="text-sm text-gray-600 dark:text-gray-400 mt-1"></p>
                </div>
                <button id="closeModal" class="p-2 hover:bg-gray-200 dark:hover:bg-gray-800 rounded-lg transition-colors">
                    <span class="material-icons text-gray-700 dark:text-gray-300">close</span>
                </button>
            </div>

            <!-- Modal Body -->
            <div class="p-6 overflow-y-auto bg-white dark:bg-gray-900" style="max-height: calc(90vh - 180px);">
                <div class="mb-6">
                    <h3 class="text-sm font-semibold text-gray-600 dark:text-gray-400 uppercase tracking-wider mb-3">Errore</h3>
                    <div class="bg-red-50 dark:bg-red-950 border-l-4 border-red-500 p-4 rounded-r-lg">
                        <pre id="modalErrorDetail" class="text-red-700 dark:text-red-300 whitespace-pre-wrap text-sm font-mono"></pre>
                    </div>
                </div>

                <div>
                    <h3 class="text-sm font-semibold text-gray-600 dark:text-gray-400 uppercase tracking-wider mb-3">Soluzione Suggerita</h3>
                    <div class="bg-green-50 dark:bg-green-950 border-l-4 border-green-500 p-4 rounded-r-lg">
                        <div class="flex items-start space-x-3">
                            <span class="material-icons text-green-600 dark:text-green-400 text-2xl">lightbulb</span>
                            <p id="modalSolution" class="text-green-700 dark:text-green-300 text-sm leading-relaxed"></p>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Modal Footer -->
            <div class="flex justify-end p-6 border-t border-gray-300 dark:border-gray-700 bg-gray-50 dark:bg-gray-800">
                <button id="closeModalBtn" class="px-6 py-2 bg-gray-300 dark:bg-gray-700 hover:bg-gray-400 dark:hover:bg-gray-600 text-gray-800 dark:text-gray-200 rounded-lg transition-colors font-medium">
                    Chiudi
                </button>
            </div>
        </div>
    </div>

    <script>
        const logDiv = document.getElementById("log");
        const statusDiv = document.getElementById("status");
        const emptyState = document.getElementById("emptyState");
        const modal = document.getElementById("errorModal");
        const themeToggle = document.getElementById("themeToggle");
        
        // Theme management
        const theme = {
            current: localStorage.getItem('theme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'),
            toggle() {
                this.current = this.current === 'dark' ? 'light' : 'dark';
                this.apply();
            },
            apply() {
                if (this.current === 'dark') {
                    document.documentElement.classList.add('dark');
                    themeToggle.querySelector('.material-icons').textContent = 'light_mode';
                } else {
                    document.documentElement.classList.remove('dark');
                    themeToggle.querySelector('.material-icons').textContent = 'dark_mode';
                }
                localStorage.setItem('theme', this.current);
            }
        };
        
        // Apply theme immediately
        theme.apply();

        themeToggle.addEventListener('click', () => theme.toggle());

        // WebSocket connection
        const ws = new WebSocket(`ws://${location.host}/ws`);

        ws.onopen = () => {
            statusDiv.innerHTML = `
                <span class="w-2 h-2 rounded-full bg-green-500 animate-pulse"></span>
                <span class="text-sm font-medium text-green-600 dark:text-green-400">Online</span>
            `;
            statusDiv.className = "flex items-center space-x-2 px-4 py-2 rounded-full bg-green-100 dark:bg-green-950 border border-green-300 dark:border-green-800";
        };

        ws.onclose = () => {
            statusDiv.innerHTML = `
                <span class="w-2 h-2 rounded-full bg-red-500 animate-pulse"></span>
                <span class="text-sm font-medium text-red-600 dark:text-red-400">Offline</span>
            `;
            statusDiv.className = "flex items-center space-x-2 px-4 py-2 rounded-full bg-red-100 dark:bg-red-950 border border-red-300 dark:border-red-800";
        };

        ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            
            emptyState.classList.add('hidden');
            
            const card = document.createElement("div");
            card.className = "error-card p-6 rounded-xl shadow-lg hover:shadow-2xl cursor-pointer border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 animate-slide-in";
            
            const languageColors = {
                'python': { light: 'from-blue-400 to-blue-600', dark: 'from-blue-500 to-blue-700' },
                'javascript': { light: 'from-yellow-400 to-yellow-600', dark: 'from-yellow-500 to-yellow-700' },
                'java': { light: 'from-red-400 to-red-600', dark: 'from-red-500 to-red-700' },
                'default': { light: 'from-purple-400 to-purple-600', dark: 'from-purple-500 to-purple-700' }
            };
            
            const colorClass = languageColors[data.language?.toLowerCase()] || languageColors.default;
            
            // Escape HTML to prevent XSS
            const escapeHtml = (text) => {
                const div = document.createElement('div');
                div.textContent = text;
                return div.innerHTML;
            };
            
            card.innerHTML = `
                <div class="flex items-start justify-between mb-4">
                    <div class="flex items-center space-x-3">
                        <div class="w-12 h-12 bg-gradient-to-br ${colorClass.light} rounded-lg flex items-center justify-center shadow-lg">
                            <span class="material-icons text-white">terminal</span>
                        </div>
                        <div>
                            <h3 class="font-bold text-lg text-gray-900 dark:text-gray-100">${escapeHtml(data.container)}</h3>
                            <span class="inline-block px-3 py-1 text-xs font-semibold bg-gradient-to-r ${colorClass.light} text-white rounded-full mt-1">
                                ${escapeHtml(data.language)}
                            </span>
                        </div>
                    </div>
                    <span class="text-xs text-gray-500 dark:text-gray-400">${new Date().toLocaleTimeString('it-IT')}</span>
                </div>
                
                <div class="space-y-3">
                    <div class="bg-red-50 dark:bg-red-950 border-l-4 border-red-500 p-3 rounded-r">
                        <p class="text-sm text-red-700 dark:text-red-300 font-mono line-clamp-2">${escapeHtml(data.error)}</p>
                    </div>
                    
                    <div class="bg-green-50 dark:bg-green-950 border-l-4 border-green-500 p-3 rounded-r">
                        <div class="flex items-start space-x-2">
                            <span class="material-icons text-green-600 dark:text-green-400 text-lg mt-0.5">lightbulb</span>
                            <p class="text-sm text-green-700 dark:text-green-300 line-clamp-2">${escapeHtml(data.solution)}</p>
                        </div>
                    </div>
                </div>
            `;
            
            card.addEventListener("click", () => {
                document.getElementById("modalContainerName").textContent = `${data.container} (${data.language})`;
                document.getElementById("modalErrorDetail").textContent = data.error;
                document.getElementById("modalSolution").textContent = data.solution;
                modal.classList.remove('hidden');
                modal.classList.add('flex');
            });

            logDiv.appendChild(card);
            logDiv.scrollTop = logDiv.scrollHeight;
        };

        // Modal controls
        document.getElementById("closeModal").addEventListener("click", () => {
            modal.classList.add('hidden');
            modal.classList.remove('flex');
        });
        
        document.getElementById("closeModalBtn").addEventListener("click", () => {
            modal.classList.add('hidden');
            modal.classList.remove('flex');
        });
        
        modal.addEventListener("click", (e) => {
            if (e.target === modal) {
                modal.classList.add('hidden');
                modal.classList.remove('flex');
            }
        });

        // Clear logs
        document.getElementById("clear").addEventListener("click", () => {
            logDiv.innerHTML = '';
            emptyState.classList.remove('hidden');
        });
    </script>
</body>
</html>
"""
@app.get("/")
async def dashboard():
    return HTMLResponse(html)

@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket):
    await ws.accept()
    active_clients.append(ws)
    for ev in recent_events[-10:]:
        await ws.send_json(ev)
    try:
        while True:
            await ws.receive_text()
    except Exception:
        pass
    finally:
        active_clients.remove(ws)

async def broadcast_json(event: dict):
    dead = []
    for ws in active_clients:
        try:
            await ws.send_json(event)
        except Exception:
            dead.append(ws)
    for ws in dead:
        if ws in active_clients:
            active_clients.remove(ws)


# -------------------------------
# 🧠 MAIN
# -------------------------------
async def main_async(args):
    solver = ErrorSolver(model=args.model, log_file=args.log_file, output_language=args.lang)
    monitor = DockerMonitor(solver, json_log_path=args.json_log, filter_pattern=args.filter)

    await monitor.monitor_existing_containers()
    asyncio.create_task(monitor.listen_for_new_containers())

    config = uvicorn.Config(app, host="0.0.0.0", port=8008, log_level="info")
    server = uvicorn.Server(config)
    await server.serve()


def main():
    parser = argparse.ArgumentParser(description="ErrorSolver Docker Daemon con dashboard e filtro container.")
    parser.add_argument("--model", help="Override modello ErrorSolver.")
    parser.add_argument("--log-file", default="logs/docker_daemon.log", help="File di log testuale.")
    parser.add_argument("--json-log", default="logs/docker_events.jsonl", help="File JSON di log errori.")
    parser.add_argument("--lang", default="italiano", help="Lingua di risposta del solver.")
    parser.add_argument("--filter", help="Regex o parole chiave per filtrare i container (es: 'web|api|nginx').")
    args = parser.parse_args()

    loop = asyncio.get_event_loop()
    try:
        loop.run_until_complete(main_async(args))
    except KeyboardInterrupt:
        print("\n🛑 Interrotto manualmente.")
    finally:
        loop.close()


if __name__ == "__main__":
    from dotenv import load_dotenv
    load_dotenv()
    main()
