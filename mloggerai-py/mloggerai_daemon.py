# daemon.py
import argparse
import asyncio
import json
import os
import re
import shlex
from datetime import datetime
from mloggerai.errorsolver import ErrorSolver

# --- REGEX MULTI-LINGUAGGIO ---
LANGUAGE_PATTERNS = {
    "python": re.compile(r"(Traceback \(most recent call last\):|File \".+\.py\", line \d+|Exception|Error: .+)", re.IGNORECASE),
    "java": re.compile(r"(Exception in thread .+|at [\w\.]+\(.*\.java:\d+\)|Caused by: .+)", re.IGNORECASE),
    "rust": re.compile(r"(thread '.*' panicked at|RUST_BACKTRACE|error:\s.+|note:\s.+)", re.IGNORECASE),
    "javascript": re.compile(r"(TypeError:|ReferenceError:|SyntaxError:|UnhandledPromiseRejectionWarning|at .+\(.+:\d+:\d+\))", re.IGNORECASE),
    "kotlin": re.compile(r"(Exception in thread .+|at .+\.kt:\d+|Caused by: .+)", re.IGNORECASE),
}

ERROR_KEYWORDS = ["error", "exception", "failed", "panic", "fatal", "traceback", "segfault"]
EVENT_WINDOW_SECONDS = 1.5


class StreamMonitor:
    def __init__(self, solver: ErrorSolver, json_log_path="logs/events.jsonl", language="italiano"):
        self.solver = solver
        self.language = language
        self._buffer = []
        self._last_time = None
        self.json_log_path = json_log_path
        os.makedirs(os.path.dirname(json_log_path), exist_ok=True)

    def _detect_language(self, text: str) -> str:
        for lang, pattern in LANGUAGE_PATTERNS.items():
            if pattern.search(text):
                return lang
        return "generic"

    def _matches_any_language(self, line: str) -> bool:
        for lang, pattern in LANGUAGE_PATTERNS.items():
            if pattern.search(line):
                return True
        return False

    def _is_error_line(self, line: str) -> bool:
        l = line.lower()
        return any(k in l for k in ERROR_KEYWORDS) or self._matches_any_language(line)

    async def handle_line(self, line: str):
        from datetime import datetime
        now = datetime.now()
        line = line.rstrip("\n")
        if not line:
            return
        if self._is_error_line(line):
            if self._last_time is None or (now - self._last_time).total_seconds() > EVENT_WINDOW_SECONDS:
                self._buffer = [line]
            else:
                self._buffer.append(line)
            self._last_time = now
            await self._process_buffer()
        elif self._last_time and (now - self._last_time).total_seconds() <= EVENT_WINDOW_SECONDS:
            self._buffer.append(line)
            self._last_time = now

    async def _process_buffer(self):
        if not self._buffer:
            return
        text = "\n".join(self._buffer)
        language_detected = self._detect_language(text)
        timestamp = datetime.now().isoformat()
        prompt_text = f"[{timestamp}] ({language_detected})\n{text}"

        try:
            solution = self.solver.solve_from_log(prompt_text)
        except Exception as e:
            solution = f"Errore interno nel solver: {e}"

        event_data = {
            "timestamp": timestamp,
            "language": language_detected,
            "error": text,
            "solution": solution,
        }

        # log testuale
        self.solver.logger.info(
            f"\n---\n🧩 Linguaggio: {language_detected}\n🔴 Errore:\n{text}\n\n💡 Soluzione:\n{solution}\n---\n"
        )

        # log JSON
        with open(self.json_log_path, "a", encoding="utf-8") as f:
            json.dump(event_data, f, ensure_ascii=False)
            f.write("\n")

        self._buffer = []
        self._last_time = None


async def monitor_journal(solver: ErrorSolver):
    mon = StreamMonitor(solver)
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


async def wrap_command_and_monitor(solver: ErrorSolver, command: str):
    mon = StreamMonitor(solver)
    argv = shlex.split(command)
    proc = await asyncio.create_subprocess_exec(
        *argv,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )

    async def read_stream(stream, tag=""):
        while True:
            line = await stream.readline()
            if not line:
                break
            decoded = line.decode(errors="ignore")
            solver.logger.debug(f"{tag}{decoded.strip()}")
            await mon.handle_line(decoded)

    await asyncio.gather(
        read_stream(proc.stdout, "[OUT] "),
        read_stream(proc.stderr, "[ERR] "),
    )
    rc = await proc.wait()
    solver.logger.info(f"Comando terminato con codice {rc}")
    return rc


def main():
    parser = argparse.ArgumentParser(description="Demone ErrorSolver con rilevamento linguaggio e JSON log.")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--journal", action="store_true", help="Monitora journalctl -f (systemd).")
    group.add_argument("--wrap", metavar="COMMAND", help="Esegue COMMAND e monitora stdout/stderr.")
    parser.add_argument("--model", help="Override modello ErrorSolver.")
    parser.add_argument("--log-file", default="logs/daemon.log", help="File di log.")
    parser.add_argument("--json-log", default="logs/events.jsonl", help="Log JSON strutturato.")
    parser.add_argument("--lang", default="italiano", help="Lingua di output.")
    args = parser.parse_args()

    solver = ErrorSolver(model=args.model, log_file=args.log_file, output_language=args.lang)
    loop = asyncio.get_event_loop()
    try:
        if args.journal:
            loop.run_until_complete(monitor_journal(solver))
        else:
            loop.run_until_complete(wrap_command_and_monitor(solver, args.wrap))
    except KeyboardInterrupt:
        solver.logger.info("Interrotto manualmente.")
    finally:
        loop.close()


if __name__ == "__main__":
    main()
