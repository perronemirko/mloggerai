# 🧠 ErrorSolver TS

TypeScript library for analyzing error logs and proposing on-cloud and on-premise AI solutions via OpenAI API

## 🚀 Installation

```bash
npm install
npm run build
```

## ⚙️ Usage

```ts
import { ErrorSolver } from "mloggerai-ts";

const solver = new ErrorSolver({
  model: "gpt-4o-mini",
  outputLanguage: "italiano"
});

await solver.solveFromLog("TypeError: Cannot read properties of undefined");
```

## 🧩 Configuration .env

```
OPENAI_API_KEY=sk-xxxx
OPENAI_API_MODEL=gpt-4o-mini
OPENAI_API_URL=https://api.openai.com/v1
OPENAI_API_PROMPT=Trova il bug e proponi la soluzione
```

## 🪵 Log

All logs are written to `logs/logger.log` with automatic rotation (max 5MB, 3 files).
