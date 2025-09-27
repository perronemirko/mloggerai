import logging
from pathlib import Path
from logging.handlers import RotatingFileHandler
from typing import cast

from openai import OpenAI
from openai.types.chat import ChatCompletionMessageParam
from dotenv import load_dotenv
import os


class ErrorSolver:
    def __init__(
        self,
        model=None,
        log_file="logs/logger.log",
        log_level=logging.DEBUG,
        output_language="italiano",
    ):

        load_dotenv()  # Carica le variabili da .env
        base_url: str = os.getenv("OPENAI_API_URL", "http://localhost:1234/v1")
        api_key: str = os.getenv("OPENAI_API_KEY", "")
        self.model: str = os.getenv("OPENAI_API_MODEL", None)
        if model is not None:
            self.model = model
        self.client = OpenAI(base_url=base_url, api_key=api_key)

        # Logger principale
        self.logger = logging.getLogger("AppLogger")
        self.logger.setLevel(log_level)
        self.output_language = output_language
        if not self.logger.hasHandlers():
            formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")

            # Console handler
            ch = logging.StreamHandler()
            ch.setFormatter(formatter)
            self.logger.addHandler(ch)

            # File handler
            Path(log_file).parent.mkdir(parents=True, exist_ok=True)
            fh = RotatingFileHandler(log_file, maxBytes=5_000_000, backupCount=3)
            fh.setFormatter(formatter)
            self.logger.addHandler(fh)

        # Aggancia handler AI
        self._attach_ai_handler(ai_level=logging.ERROR)

    def solve_from_log(self, text):
        try:
            system_prompt: str = os.getenv(
                "OPENAI_API_PROMPT",
                f"Trova il bug e proponi la soluzione in modo molto conciso.",
            )
            messages = cast(
                list[ChatCompletionMessageParam],
                [
                    {"role": "system", "content": f"{system_prompt}\nRispondi sempre in lingua {self.output_language}"},
                    {"role": "user", "content": text},
                ],
            )
            completion = self.client.chat.completions.create(
                model=self.model, temperature=0.3, max_tokens=150, messages=messages
            )
            return completion.choices[0].message.content.strip()
        except Exception as e:
            return f"Errore AI: {e}"

    def _attach_ai_handler(self, ai_level=logging.ERROR):
        solver = self

        class AIHandler(logging.Handler):
            def emit(self, record):
                if getattr(record, "_from_ai_solver", False):
                    return
                try:
                    msg = self.format(record)
                    solution = solver.solve_from_log(msg)
                    combined = f"📘 Soluzione AI: {solution}"
                    solver.logger.debug(combined, extra={"_from_ai_solver": True})
                except Exception as e:
                    solver.logger.debug(
                        f"Errore AI interno: {e}", extra={"_from_ai_solver": True}
                    )

        handler = AIHandler()
        handler.setLevel(ai_level)
        handler.setFormatter(
            logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
        )
        self.logger.addHandler(handler)
