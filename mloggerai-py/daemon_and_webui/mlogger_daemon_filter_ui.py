# daemon_web.py
import asyncio
import re
import shlex
from datetime import datetime
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Form
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from mloggerai.errorsolver import ErrorSolver
from dotenv import load_dotenv
load_dotenv()
ERROR_KEYWORDS = ["error", "exception", "failed", "panic", "fatal", "traceback", "segfault"]
EVENT_WINDOW_SECONDS = 1.5

app = FastAPI(title="ErrorSolver Daemon Web UI")

# 🔌 Serve file statici (frontend)
app.mount("/static", StaticFiles(directory="static"), name="static")

active_task = None
websockets = set()


class StreamMonitor:
    def __init__(self, solver: ErrorSolver, ws_broadcast, language="italiano", filter_pattern=None):
        self.solver = solver
        self.ws_broadcast = ws_broadcast
        self.language = language
        self.filter_pattern = re.compile(filter_pattern, re.IGNORECASE) if filter_pattern else None
        self._buffer = []
        self._last_time = None

    def _is_error_line(self, line: str) -> bool:
        return any(k in line.lower() for k in ERROR_KEYWORDS)

    def _match_filter(self, line: str) -> bool:
        return bool(self.filter_pattern.search(line)) if self.filter_pattern else True

    async def handle_line(self, line: str):
        now = datetime.now()
        line = line.rstrip("\n")
        if not line or not self._match_filter(line):
            return

        if self._is_error_line(line):
            if not self._last_time or (now - self._last_time).total_seconds() > EVENT_WINDOW_SECONDS:
                self._buffer = [line]
            else:
                self._buffer.append(line)
            self._last_time = now
            await self._process_buffer()
        elif self._last_time and (now - self._last_time).total_seconds() <= EVENT_WINDOW_SECONDS:
            self._buffer.append(line)
            self._last_time = now

    async def _process_buffer(self):
        text = "\n".join(self._buffer)
        try:
            solution = self.solver.solve_from_log(text)
        except Exception as e:
            solution = f"Errore interno: {e}"

        message = {
            "timestamp": datetime.now().isoformat(),
            "error": text,
            "solution": solution
        }
        await self.ws_broadcast(message)
        self._buffer = []
        self._last_time = None


async def ws_broadcast(message: dict):
    dead = set()
    for ws in websockets:
        try:
            await ws.send_json(message)
        except WebSocketDisconnect:
            dead.add(ws)
    for ws in dead:
        websockets.remove(ws)


@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket):
    await ws.accept()
    websockets.add(ws)
    try:
        while True:
            await ws.receive_text()  # keep alive
    except WebSocketDisconnect:
        websockets.remove(ws)


async def monitor_journal(solver: ErrorSolver, pattern: str):
    mon = StreamMonitor(solver, ws_broadcast, filter_pattern=pattern)
    proc = await asyncio.create_subprocess_exec(
        "journalctl", "-f", "-o", "short",
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    async def read_stream(stream):
        while True:
            line = await stream.readline()
            if not line:
                break
            await mon.handle_line(line.decode(errors="ignore"))
    await asyncio.gather(read_stream(proc.stdout), read_stream(proc.stderr))


async def wrap_command(solver: ErrorSolver, command: str):
    mon = StreamMonitor(solver, ws_broadcast)
    proc = await asyncio.create_subprocess_exec(
        *shlex.split(command),
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    async def read_stream(stream):
        while True:
            line = await stream.readline()
            if not line:
                break
            await mon.handle_line(line.decode(errors="ignore"))
    await asyncio.gather(read_stream(proc.stdout), read_stream(proc.stderr))


@app.post("/start")
async def start_monitor(mode: str = Form(...), command: str = Form(None), pattern: str = Form(None)):
    global active_task
    if active_task and not active_task.done():
        return {"status": "error", "message": "Monitor già attivo"}
    solver = ErrorSolver(log_file="logs/web_daemon.log")
    if mode == "journal":
        active_task = asyncio.create_task(monitor_journal(solver, pattern))
    elif mode == "wrap" and command:
        active_task = asyncio.create_task(wrap_command(solver, command))
    else:
        return {"status": "error", "message": "Parametri invalidi"}
    return {"status": "ok", "message": "Monitor avviato"}


@app.post("/stop")
async def stop_monitor():
    global active_task
    if active_task and not active_task.done():
        active_task.cancel()
        return {"status": "ok", "message": "Monitor fermato"}
    return {"status": "error", "message": "Nessun monitor attivo"}


@app.get("/")
async def index():
    return HTMLResponse(open("static/index.html").read())
