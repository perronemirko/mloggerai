import logging
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv
from openai import OpenAI
from openai.types.chat import ChatCompletionMessageParam

from mloggerai import ErrorSolver  # importa la classe principale


class ErrorSolverBuilder:
    def __init__(self):
        load_dotenv()
        # Parametri opzionali con default
        self._base_url: Optional[str] = None
        self._api_key: Optional[str] = None
        self._model: Optional[str] = None
        self._system_prompt: Optional[str] = None
        self._temperature: float = 0.3
        self._max_tokens: int = 180
        self._output_language: str = "italiano"
        self._log_file: str = "logs/logger.log"
        self._log_level: int = logging.DEBUG

    def with_base_url(self, url: str):
        self._base_url = url
        return self

    def with_api_key(self, key: str):
        self._api_key = key
        return self

    def with_model(self, model: str):
        self._model = model
        return self

    def with_system_prompt(self, prompt: str):
        self._system_prompt = prompt
        return self

    def with_temperature(self, temp: float):
        self._temperature = temp
        return self

    def with_max_tokens(self, tokens: int):
        self._max_tokens = tokens
        return self

    def with_output_language(self, lang: str):
        self._output_language = lang
        return self

    def with_log_file(self, log_file: str):
        self._log_file = log_file
        return self

    def with_log_level(self, level: int):
        self._log_level = level
        return self

    def build(self) -> ErrorSolver:
        return ErrorSolver(
            base_url=self._base_url,
            api_key=self._api_key,
            model=self._model,
            system_prompt=self._system_prompt,
            temperature=self._temperature,
            max_tokens=self._max_tokens,
            output_language=self._output_language,
            log_file=self._log_file,
            log_level=self._log_level,
        )
