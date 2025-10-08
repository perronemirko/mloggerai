# daemon.py
import argparse
import asyncio
import re
import shlex
from datetime import datetime
from mloggerai.errorsolver import ErrorSolver

ERROR_KEYWORDS = ["error", "exception", "failed", "panic", "fatal", "traceback", "segfault"]
EVENT_WINDOW_SECONDS = 1.5

class StreamMonitor:
    def __init__(self, solver: ErrorSolver, language="italiano", filter_pattern: str = None):
        self.solver = solver
        self.language = language
        self.filter_pattern = re.compile(filter_pattern, re.IGNORECASE) if filter_pattern else None
        self._buffer = []
        self._last_time = None

    def _is_error_line(self, line: str) -> bool:
        l = line.lower()
        return any(k in l for k in ERROR_KEYWORDS)

    def _match_filter(self, line: str) -> bool:
        """Se c’è un filtro, ritorna True solo se la riga lo contiene."""
        if self.filter_pattern:
            return bool(self.filter_pattern.search(line))
        return True  # nessun filtro => accetta tutto

    async def handle_line(self, line: str):
        now = datetime.now()
        line = line.rstrip("\n")
        if not line:
            return
        # 🔍 applica filtro
        if not self._match_filter(line):
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
        prompt_text = f"[Captured @ {datetime.now().isoformat()}]\n{text}"
        try:
            solution = self.solver.solve_from_log(prompt_text)
        except Exception as e:
            solution = f"Errore interno durante l’analisi: {e}"

        combined = f"\n---\n🔴 Errore rilevato:\n{text}\n💡 Soluzione:\n{solution}\n---\n"
        self.solver.logger.info(combined)
        self._buffer = []
        self._last_time = None


async def monitor_journal(solver: ErrorSolver, filter_pattern: str):
    """Monitora journalctl -f e applica filtro regex se specificato."""
    mon = StreamMonitor(solver, filter_pattern=filter_pattern)
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
    parser = argparse.ArgumentParser(description="Demone ErrorSolver con filtro di processo per journalctl.")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--journal", action="store_true", help="Monitora journalctl -f (systemd).")
    group.add_argument("--wrap", metavar="COMMAND", help="Esegue COMMAND e monitora stdout/stderr.")
    parser.add_argument("--model", help="Override modello ErrorSolver.")
    parser.add_argument("--log-file", default="logs/daemon.log", help="File di log.")
    parser.add_argument("--lang", default="italiano", help="Lingua di output.")
    parser.add_argument("--filter", help="Regex o parole chiave da cercare nei log (es: 'python|nginx|myservice').")
    args = parser.parse_args()

    solver = ErrorSolver(model=args.model, log_file=args.log_file, output_language=args.lang)

    loop = asyncio.get_event_loop()
    try:
        if args.journal:
            loop.run_until_complete(monitor_journal(solver, args.filter))
        else:
            loop.run_until_complete(wrap_command_and_monitor(solver, args.wrap))
    except KeyboardInterrupt:
        solver.logger.info("Interrotto manualmente.")
    finally:
        loop.close()


if __name__ == "__main__":
    main()
