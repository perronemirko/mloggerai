# docker_daemon.py
import argparse
import asyncio
import json
import re
import subprocess
from datetime import datetime
from mloggerai.errorsolver import ErrorSolver

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


class DockerMonitor:
    """Monitora i log di più container Docker e invia gli errori a ErrorSolver."""

    def __init__(self, solver: ErrorSolver, json_log_path="logs/docker_events.jsonl"):
        self.solver = solver
        self.json_log_path = json_log_path
        self._buffers = {}  # container -> list di linee
        self._last_times = {}
        self._active_tasks = {}  # container -> task asyncio
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

        # log testuale
        self.solver.logger.info(
            f"\n🧩 [{container}] ({language})\n🔴 Errore:\n{text}\n💡 Soluzione:\n{solution}\n---"
        )

        # log JSON
        with open(self.json_log_path, "a", encoding="utf-8") as f:
            json.dump(event, f, ensure_ascii=False)
            f.write("\n")

        self._buffers[container] = []
        self._last_times[container] = None

    async def attach_to_container(self, container_name: str):
        """Attacca il monitor a un container già esistente."""
        if container_name in self._active_tasks:
            return  # già attaccato
        self.solver.logger.info(f"🔗 Attacco ai log di {container_name}...")
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
        self.solver.logger.info(f"📴 Container {container_name} terminato o log chiuso.")
        self._active_tasks.pop(container_name, None)

    async def monitor_existing_containers(self):
        """Attacca ai container già avviati."""
        proc = subprocess.run(["docker", "ps", "--format", "{{.Names}}"], capture_output=True, text=True)
        containers = [c.strip() for c in proc.stdout.splitlines() if c.strip()]
        if not containers:
            self.solver.logger.warning("Nessun container attivo trovato.")
        for c in containers:
            await self.attach_to_container(c)

    async def listen_for_new_containers(self):
        """Ascolta docker events per nuovi container (start)."""
        self.solver.logger.info("🎧 Ascolto eventi Docker (start/stop)...")
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
                self.solver.logger.info(f"🆕 Nuovo container avviato: {name}")
                await self.attach_to_container(name)
            elif status == "die":
                if name in self._active_tasks:
                    self.solver.logger.info(f"💀 Container terminato: {name}")
                    self._active_tasks.pop(name, None)


async def main_async(args):
    solver = ErrorSolver(model=args.model, log_file=args.log_file, output_language=args.lang)
    monitor = DockerMonitor(solver, json_log_path=args.json_log)

    await monitor.monitor_existing_containers()
    await monitor.listen_for_new_containers()  # resta in ascolto per nuovi container


def main():
    parser = argparse.ArgumentParser(description="Demone ErrorSolver per log Docker (auto-attach ai container nuovi).")
    parser.add_argument("--model", help="Override modello ErrorSolver.")
    parser.add_argument("--log-file", default="logs/docker_daemon.log", help="File di log testuale.")
    parser.add_argument("--json-log", default="logs/docker_events.jsonl", help="File JSON di log errori.")
    parser.add_argument("--lang", default="italiano", help="Lingua per la risposta del solver.")
    args = parser.parse_args()

    loop = asyncio.get_event_loop()
    try:
        loop.run_until_complete(main_async(args))
    except KeyboardInterrupt:
        print("\n🛑 Interrotto manualmente.")
    finally:
        loop.close()


if __name__ == "__main__":
    main()
