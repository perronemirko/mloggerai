import fs from "fs";
import path from "path";
import dotenv from "dotenv";
import { fileURLToPath } from "url";
import winston from "winston";
import { OpenAI } from "openai";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export class ErrorSolver {
  constructor({
    model = null,
    base_url = null,
    api_Key = null,
    temperature = 0.3,
    max_tokens = 150,
    base_prompt = null,
    logFile = "logs/logger.log",
    logLevel = "debug",
    outputLanguage = "italiano",
  } = {}) {
    this.baseURL =
      base_url || process.env.OPENAI_API_URL || "http://localhost:1234/v1";
    this.apiKey = api_Key || process.env.OPENAI_API_KEY || "";
    this.model = model || process.env.OPENAI_API_MODEL || "";
    this.client = new OpenAI({
      baseURL: this.baseURL,
      apiKey: this.apiKey,
    });
    this.outputLanguage = outputLanguage;
    this.temperature = temperature || 0.3;
    this.max_tokens = max_tokens || 150;
    this.systemPrompt =
      base_prompt ||
      process.env.OPENAI_API_PROMPT ||
      "Trova il bug e proponi la soluzione in modo molto conciso fornendo anche un solo esempio di codice corretto";
    const logDir = path.dirname(logFile);
    if (!fs.existsSync(logDir)) {
      fs.mkdirSync(logDir, { recursive: true });
    }
    /** @type {import("winston").Logger & { error(msg: string): Promise<string | undefined> }} */
    this.logger = winston.createLogger({
      level: logLevel,
      format: winston.format.combine(
        winston.format.timestamp(),
        winston.format.printf(
          ({ timestamp, level, message }) =>
            `${timestamp} - ${level.toUpperCase()} - ${message}`
        )
      ),
      transports: [
        new winston.transports.Console(),
        new winston.transports.File({
          filename: logFile,
          maxsize: 5000000,
          maxFiles: 3,
        }),
      ],
    });

    this._attachAIHandler("error");
  }

  async solveFromLog(text) {
    try {
      const completion = await this.client.chat.completions.create({
        model: this.model,
        temperature: this.temperature,
        max_tokens: this.max_tokens,
        messages: [
          {
            role: "system",
            content: `${this.systemPrompt}. Rispondi sempre in lingua ${this.outputLanguage}`,
          },
          { role: "user", content: text },
        ],
      });

      return completion.choices[0].message.content.trim();
    } catch (err) {
      return `Errore AI: ${err.message}`;
    }
  }

  _attachAIHandler(level) {
    const origError = this.logger[level].bind(this.logger);
    this.logger[level] = async (message, ...args) => {
      origError(message, ...args);
      try {
        const solution = await this.solveFromLog(message);
        this.logger.debug(`🧠💡 Soluzione AI: ${solution}`);
        return solution;
      } catch (err) {
        this.logger.debug(`Errore AI interno: ${err.message}`);
        return undefined;
      }
    };
  }
}
