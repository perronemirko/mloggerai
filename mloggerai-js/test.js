import { ErrorSolver } from "./mloggerai.js";
const OPENAI_API_URL = "http://localhost:1234/v1";
const OPENAI_API_MODEL = "lmstudio-community/llama-3.2-3b-instruct";
const OPENAI_API_KEY = "lm-studio";
const OPENAI_API_PROMPT = " Elabora il tutto in italiano e rispndi con un esempio.";
const logger = new ErrorSolver({
  model: OPENAI_API_MODEL,
  api_key: OPENAI_API_KEY,
  base_url: OPENAI_API_URL,
  base_prompt: OPENAI_API_PROMPT,
}).logger;

// logger.info("Applicazione avviata");
// logger.error("ReferenceError: x is not defined");
import express from "express";
const app = express();
const port = 3000;

app.get("/api", async (req, res) => {
  try {
    // Simula un errore
    throw new Error("Errore simulato");
    res.json({ message: "Successo!" });
  } catch (err) {
    const e = await logger.error(err.message);
    console.error(e);
    res.status(500).json({ error: e });
  }
});

app.listen(port, () => {
  console.log(`Server in ascolto su http://localhost:${port}`);
});
