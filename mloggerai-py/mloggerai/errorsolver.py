import logging
import os
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import cast

from dotenv import load_dotenv
from openai import OpenAI
from openai.types.chat import ChatCompletionMessageParam


class ErrorSolver:
    def __init__(
        self,
        base_url: str | None = None,
        api_key: str | None = None,
        model: str | None = None,
        system_prompt: str | None = None,
        temperature: float = 0.3,
        max_tokens: int = 180,
        output_language: str = "italiano",
        log_file: str = "logs/logger.log",
        log_level: int = logging.DEBUG,
    ):
        # Carica variabili da .env
        load_dotenv()
        self.base_url: str = base_url or os.getenv("OPENAI_API_URL", "http://localhost:1234/v1")
        self.api_key: str = api_key or os.getenv("OPENAI_API_KEY", "")
        self.model: str = model or os.getenv("OPENAI_API_MODEL", "")
        self.system_prompt: str = system_prompt or os.getenv(
            "OPENAI_API_PROMPT",
            "Trova il bug e proponi la soluzione in modo molto conciso fornendo un solo esempio corretto.",
        )
        self.temperature = temperature
        self.max_tokens = max_tokens
        self.output_language = output_language

        # Configura client OpenAI
        self.client = OpenAI(base_url=self.base_url, api_key=self.api_key)

        # Configura logger
        self.logger = logging.getLogger("AppLogger")
        self.logger.setLevel(log_level)
        if not self.logger.hasHandlers():
            self._setup_handlers(log_file)

        # Attacca AI handler per i messaggi di errore
        self._attach_ai_handler(ai_level=logging.ERROR)

    def _setup_handlers(self, log_file: str):
        formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")

        # Console
        ch = logging.StreamHandler()
        ch.setFormatter(formatter)
        self.logger.addHandler(ch)

        # File (rotating)
        Path(log_file).parent.mkdir(parents=True, exist_ok=True)
        fh = RotatingFileHandler(log_file, maxBytes=5_000_000, backupCount=3)
        fh.setFormatter(formatter)
        self.logger.addHandler(fh)

    def error(self, text: str) -> str:
        try:
            messages = cast(
                list[ChatCompletionMessageParam],
                [
                    {
                        "role": "system",
                        "content": f"{self.system_prompt}\nRispondi sempre in lingua {self.output_language} con un solo esempio di codice corretto",
                    },
                    {"role": "user", "content": text},
                ],
            )
            completion = self.client.chat.completions.create(
                model=self.model,
                temperature=self.temperature,
                max_tokens=self.max_tokens,
                messages=messages,
            )
            return completion.choices[0].message.content.strip()
        except Exception as e:
            return f"Errore AI: {e}"

    def _attach_ai_handler(self, ai_level=logging.ERROR):
        solver = self

        class AIHandler(logging.Handler):
            def emit(self, record: logging.LogRecord):
                # Evita ricorsione
                if getattr(record, "_from_ai_solver", False):
                    return
                try:
                    msg = self.format(record)
                    solution = solver.error(msg)
                    combined = f"🧠💡 Soluzione AI: {solution}"
                    solver.logger.debug(combined, extra={"_from_ai_solver": True})
                except Exception as e:
                    solver.logger.debug(
                        f"Errore AI interno: {e}", extra={"_from_ai_solver": True}
                    )

        handler = AIHandler()
        handler.setLevel(ai_level)
        handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
        self.logger.addHandler(handler)
