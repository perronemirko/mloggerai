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
```

## USAGE
```javascript 
import { ErrorSolver } from "mloggerai";

const solver = new ErrorSolver({
  model: "<your-model>",
  outputLanguage: "english",
});

const logger = solver.logger;

try {
  const x = 1 / 0;
} catch (err) {
  // Log dell’errore; l’AI intercetta e stampa solo la soluzione
  logger.error("Caught an exception: " + err.message);
}
```
Output:

vbnet

📘 AI Solution: Bug: Division by zero. Modify the operation to avoid dividing by zero.
## Example 2: Custom log file and log level
```javascript

import { ErrorSolver } from "mloggerai";

const solver = new ErrorSolver({
  model: "<your-model>",
  logFile: "logs/my_custom.log",
  logLevel: "info",
  outputLanguage: "italiano",
});

const logger = solver.logger;

try {
  const myList = [];
  console.log(myList[1]); // IndexError
} catch (err) {
  logger.error("Caught an exception: " + err.message);
}
```
Output (console e file logs/my_custom.log):

📘 Soluzione AI: Bug: Indice fuori intervallo. Controllare che l'elemento esista prima di accedere all'indice.
## Example 3: Logging multiple exceptions
```javascript
import { ErrorSolver } from "mloggerai";

const solver = new ErrorSolver({ model: "<your-model>" });
const logger = solver.logger;

for (const val of [0, "a", null]) {
  try {
    const result = 10 / val;
  } catch (err) {
    logger.error(`Error with value: ${val} -> ${err.message}`);
  }
}
```
Output:

pgsql

📘 AI Solution: Bug: Division by zero or invalid type. Ensure the value is a non-zero number.
📘 AI Solution: Bug: Division by zero or invalid type. Ensure the value is a non-zero number.
📘 AI Solution: Bug: Division by zero or invalid type. Ensure the value is a non-zero number.
Advanced Configuration
logFile: percorso del file di log (default: logs/logger.log)

logLevel: livello di logging (default: debug)

outputLanguage: lingua della risposta AI (default: "italiano")

```javascript

const solver = new ErrorSolver({
  model: "<your-model>",
  logFile: "logs/mylog.log",
  logLevel: "info",
  outputLanguage: "italian",
});
```
## Configuration with .env
Create a .env file in the project root to configure the defaults:
```bash
OPENAI_API_URL="http://localhost:1234/v1"
OPENAI_API_KEY="<MY_KEY>"
OPENAI_API_MODEL="<MY_MODEL>"
OPENAI_API_PROMPT="find the bug and always propose the best solution in a very concise way"
```
