# AiLogger

**AiLogger** is a Python package that automatically analyzes Python tracebacks using AI models (OpenAI / LM Studio) and provides concise bug fixes directly in the logs.

## Features

- Automatically intercepts logged errors (`ERROR`) and sends them to AI.
- Prints only the **AI solution**, avoiding redundant messages or debug noise.
- Supports customizable output language (e.g., English, Italian).
- Logs can be saved both to console and to a file using **RotatingFileHandler**.

## Installation

Install directly from the Git repository:

```bash
pip install git+ssh://git@github.com:perronemirko/ailogger.git
