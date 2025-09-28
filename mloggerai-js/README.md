# MLoggerAI (Node.js)

**MLoggerAI** is a Node.js module for automatically analyzing logged errors using AI models (OpenAI / LM Studio / Ollama / llama.cpp) and providing concise fixes directly within the logs.


## Features

- Automatically intercepts logged errors (logger.error) and sends them to the AI.

- Prints only the **AI solution**, avoiding redundant messages or debug noise.

- Supports customizable output language (e.g., English, Italian).

- Logs can be saved to both the **console** and **file** via **Winston** (with file rotation).
## Installation
Install using npm:

```bash
npm install mloggerai
