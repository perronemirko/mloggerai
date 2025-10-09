import fs from "fs";
import path from "path";
import dotenv from "dotenv";
import { fileURLToPath } from "url";
import winston, { Logger } from "winston";
import { OpenAI } from "openai";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export interface ErrorSolverConfig {
  model?: string | null;
  base_url?: string | null;
  api_Key?: string | null;
  temperature?: number;
  max_tokens?: number;
  base_prompt?: string | null;
  logFile?: string;
  logLevel?: string;
  outputLanguage?: string;
}

export class ErrorSolver {
  private baseURL: string;
  private apiKey: string;
  private model: string;
  private client: OpenAI;
  private outputLanguage: string;
  private temperature: number;
  private max_tokens: number;
  private systemPrompt: string;
  private logger: Logger;

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
  }: ErrorSolverConfig = {}) {
    this.baseURL =
      base_url || process.env.OPENAI_API_URL || "http://localhost:1234/v1";
    this.apiKey = api_Key || process.env.OPENAI_API_KEY || "";
    this.model = model || process.env.OPENAI_API_MODEL || "";
    this.client = new OpenAI({
      baseURL: this.baseURL,
      apiKey: this.apiKey,
    });

    this.outputLanguage = outputLanguage;
    this.temperature = temperature;
    this.max_tokens = max_tokens;
    this.systemPrompt =
      base_prompt ||
      process.env.OPENAI_API_PROMPT ||
      "Trova il bug e proponi la soluzione in modo molto conciso fornendo anche un solo esempio di codice corretto";
    // Ensure log directory exists without constructing fs.Stats manually
    const logDir = path.dirname(logFile);
    try {
      if (!fs.existsSync(logDir)) {
        fs.mkdirSync(logDir, { recursive: true });
      }
    } catch (err) {
      console.error(`Cannot create log directory: ${err}`);
    }

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
          maxsize: 5_000_000,
          maxFiles: 3,
        }),
      ],
    });

    this._attachAIHandler("error");
  }

  async solveFromLog(text: string): Promise<string> {
    try {
      // 🔹 Log info delle variabili principali
      this.logger.debug(`baseURL: ${this.baseURL}`);
      this.logger.debug(`apiKey: ${this.apiKey ? "[SET]" : "[EMPTY]"}`); // nascondi chiave completa
      this.logger.debug(`model: ${this.model}`);
      this.logger.debug(`outputLanguage: ${this.outputLanguage}`);
      this.logger.debug(`temperature: ${this.temperature}`);
      this.logger.debug(`max_tokens: ${this.max_tokens}`);
      this.logger.debug(`systemPrompt: ${this.systemPrompt}`);

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

      return completion.choices[0].message?.content?.trim() || "";
    } catch (err: any) {
      return `Errore AI: ${err.message}`;
    }
  }

  private _attachAIHandler(level: keyof Logger): void {
    const origLog = (this.logger[level] as Function).bind(this.logger);
    (this.logger[level] as any) = async (message: string, ...args: unknown[]) => {
      origLog(message, ...args);
      try {
        const solution = await this.solveFromLog(message);
        this.logger.debug(`🧠💡 Soluzione AI: ${solution}`);
        return solution;
      } catch (err: any) {
        this.logger.debug(`Errore AI interno: ${err.message}`);
        return undefined;
      }
    };
  }
}
