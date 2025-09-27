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
pip install "git+ssh://git@github.com/perronemirko/ailogger.git"


Usage Examples
Example 1: Basic usage
python
￼Copy code
from ailogger.errosolver import ErrorSolver
import logging

## Configuration with .env

Create a `.env` file in the project root for defaults:
OPENAI_API_URL
OPENAI_API_KEY
OPENAI_API_MODEL
# Initialize ErrorSolver with model and desired language
solver = ErrorSolver(
    model="<YOUR_LLM_MODEL>",
    output_language="english"
)

logger = solver.logger

try:
    x = 1 / 0
except Exception as e:
    # Logs the error; AI handler intercepts and prints only AI solution
    logger.error("Caught an exception", exc_info=e)
Output:

vbnet
￼Copy code
📘 AI Solution: Bug: Division by zero. Modify the operation to avoid dividing by zero.
Example 2: Using a custom log file and log level
python
￼Copy code
from ailogger.errosolver import ErrorSolver
import logging

solver = ErrorSolver(
    model="<YOUR_LLM_MODEL>",
    log_file="logs/my_custom.log",
    log_level=logging.INFO,
    output_language="italian"
)

logger = solver.logger

try:
    my_list = []
    print(my_list[1])  # IndexError
except Exception as e:
    logger.error("Caught an exception", exc_info=e)
Output (console and file logs/my_custom.log):

rust
￼Copy code
📘 Soluzione AI: Bug: Indice fuori intervallo. Controllare che l'elemento esista prima di accedere all'indice.
Example 3: Logging multiple exceptions
python
￼Copy code
from ailogger.errosolver import ErrorSolver
import logging

solver = ErrorSolver(model="<YOUR_LLM_MODEL>")
logger = solver.logger

for val in [0, "a", None]:
    try:
        result = 10 / val
    except Exception as e:
        logger.error(f"Error with value: {val}", exc_info=e)
Output:

pgsql
￼Copy code
📘 AI Solution: Bug: Division by zero or invalid type. Ensure the value is a non-zero number.
📘 AI Solution: Bug: Division by zero or invalid type. Ensure the value is a non-zero number.
📘 AI Solution: Bug: Division by zero or invalid type. Ensure the value is a non-zero number.
Advanced Configuration
log_file: path to the log file (default: logs/server.log)

log_level: logging level (default: DEBUG)

output_language: language of the AI response (default: "english")

python
￼Copy code
solver = ErrorSolver(
    model="<YOUR_LLM_MODEL>",
    log_file="logs/mylog.log",
    log_level=logging.INFO,
    output_language="italian"
)

