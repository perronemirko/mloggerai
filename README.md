# MLoggerAI

**MLoggerAI** is a multi-language package for automatically analyzing tracebacks and runtime errors using AI models (OpenAI / LM Studio) and providing concise bug fixes directly in the logs.  
It supports **Python, Java, and JavaScript**.

## Features

- Automatically intercepts logged errors and sends them to AI.
- Prints only the **AI solution**, avoiding redundant messages or debug noise.
- Supports multiple languages in output (e.g., English, Italian).
- Logs can be saved both to console and to a file.
- Supports Python, Java, and JavaScript projects with minimal integration.

## Installation

Install directly from the Git repository:

```bash
pip install "git+ssh://git@github.com/perronemirko/mloggerai.git"
npm install mloggerai
```
# Usage Examples
## Python Example: Basic Usage

```python
from mloggerai.errorsolver import ErrorSolver
import logging

# Initialize ErrorSolver
solver = ErrorSolver(
    model="<YOUR_LLM_MODEL>",
    output_language="english"
)

logger = solver.logger

try:
    x = 1 / 0
except Exception as e:
    logger.error("Caught an exception", exc_info=e)
```



## JavaScript Example
```javascript
import { ErrorSolver } from "./mloggerai.js";

const logger = new ErrorSolver().logger;

logger.info("Applicazione avviata");
logger.error("ReferenceError: x is not defined");
```

## Java Example
clone the project build and use it as a third-party library
```java
package com.mloggerai;

public class Main {
    public static void main(String[] args) {
        // Inizializza ErrorSolver
        ErrorSolver solver = new ErrorSolver();

        // Messaggio di esempio da analizzare
        String errorMessage = "NullPointerException at line 42 in UserService.java";

        System.out.println("➡️ Inviando errore al solver AI...");
        String solution = solver.solveFromLog(errorMessage);

        System.out.println("✅ Risposta AI:");
        System.out.println(solution);

        // Chiudi risorse
        solver.shutdown();
    }
}

```
Output:

📘 AI Solution: Bug: Division by zero. Modify the operation to avoid dividing by zero.
test_errorsolver.py", line 8, in main
    1 / 0
ZeroDivisionError: division by zero
2025-09-28 20:40:56,006 - DEBUG - 📘 Soluzione AI: **Risoluzione del bug**

Il bug è causato dal tentativo di dividere per zero nel codice. La soluzione è semplice:

Aggiungi una condizione per verificare se il divisor non è nullo prima di effettuare la divisione.

Esempio:
```python
try:
    result = 1 / (divisor != 0)
except ZeroDivisionError:
    print("Errore: division by zero")
```
In questo modo, si evita l'errore e si gestisce il caso in cui il divisor è nullo.
