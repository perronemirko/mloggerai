**MLoggerAI** is a Python package for automatically analyzing Python tracebacks using AI models (OpenAI / LM Studio) and providing concise bug fixes directly in the logs.

## Features

- Automatically intercepts logged errors (`ERROR`) and sends them to AI.
- Prints only the **AI solution**, avoiding redundant messages or debug noise.
- Supports customizable output language (e.g., English, Italian).
- Logs can be saved both to console and to a file using **RotatingFileHandler**.

## Installation

Install directly from the Git repository:

```bash
pip install "git+ssh://git@github.com/perronemirko/mloggerai.git"
```

Usage Examples
Example 1: Basic usage
```python
from mloggerai.errorsolver import ErrorSolver
from dotenv import load_dotenv
load_dotenv()  # Carica le variabili da .env
def main():
    solver = ErrorSolver()
    logger = solver.logger

    try:
        1 / 0
    except Exception as e:
        logger.error("Errore catturato", exc_info=e)

if __name__ == "__main__":
    main()
```
## Configuration with .env

Create a `.env` file in the project root for defaults:
OPENAI_API_URL="<llama.cpp| Ollama| Openai|lmstudio URLS:PORT/v1">
OPENAI_API_KEY="<MY_KEY>"
OPENAI_API_MODEL="<MY_MODEL>"
OPENAI_API_PROMPT="find the bug and alwayse propose the best solution in a very concise way"
# Initialize ErrorSolver with model and desired language
```python
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
```
Output:
```bash
🧠💡 AI Solution: Bug: Division by zero. Modify the operation to avoid dividing by zero.
```

Example 2: Using a custom log file and log level
```python
from mloogerai.errorsolver import ErrorSolver
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
```
```bash
🧠💡 Soluzione AI: Bug: Indice fuori intervallo. Controllare che l'elemento esista prima di accedere all'indice.
Example 3: Logging multiple exceptions
```
```python
from mloggerai.errorsolver import ErrorSolver
import logging

solver = ErrorSolver(model="<YOUR_LLM_MODEL>")
logger = solver.logger

for val in [0, "a", None]:
    try:
        result = 10 / val
    except Exception as e:
        logger.error(f"Error with value: {val}", exc_info=e)
```

```bash
🧠💡 AI Solution: Bug: Division by zero or invalid type. Ensure the value is a non-zero number.
```
Advanced Configuration
```python
from mloggerai import ErrorSolverBuilder

logger = (
    ErrorSolverBuilder()
    .with_model("lmstudio-community/llama-3.2-3b-instruct")
    .with_output_language("inglese")
    .with_log_file("logs/custom.log")
    .build()
).logger

logger.info("Applicazione avviata")
for val in [0, "a", None]:
    try:
        result = 10 / val
    except Exception as e:
        logger.error(f"Error with value: {val}", exc_info=e)

```
```bash

TypeError: unsupported operand type(s) for /: 'int' and 'str'
2025-10-11 12:57:07,736 - DEBUG - 🧠💡 Soluzione AI: Il bug è che il tipo di `val` non è stato verificato e potrebbe essere una stringa. 

Soluzione:
```python
# Invece di utilizzare un tipo di dati generico, specificare il tipo di dati corretto
result = 10 / int(val)
```
In questo modo, si assicura che `val` sia sempre un numero intero prima di tentare la divisione.
2025-10-11 12:57:07,736 - ERROR - Error with value: None
Traceback (most recent call last):
  File "/home/albus/PycharmProjects/mloggerai/mloggerai-py/tests/test_errorsolverbuilder.py", line 14, in <module>
    result = 10 / val
TypeError: unsupported operand type(s) for /: 'int' and 'NoneType'
2025-10-11 12:57:08,192 - DEBUG - 🧠💡 Soluzione AI: Il bug è che il valore `val` è `None`, quindi non può essere utilizzato per la divisione.

La soluzione è controllare se il valore è `None` prima di tentare la divisione. 

Esempio corretto:
```python
result = 10 / (val if val is not None else 1)
```
In questo modo, se `val` è `None`, il risultato sarà `10`.
```