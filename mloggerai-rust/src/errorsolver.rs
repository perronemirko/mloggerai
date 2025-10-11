use core::f32;
use std::env;
use std::fs::{OpenOptions, create_dir_all};
use std::io::Write;
use std::path::Path;

use reqwest::blocking::Client;
use serde_json::{Value, json};

/// Configuration struct for ErrorSolver
#[derive(Default)]
pub struct ErrorSolverConfig {
    pub base_url: Option<String>,
    pub api_key: Option<String>,
    pub model: Option<String>,
    pub system_prompt: Option<String>,
    pub temperature: Option<f32>,
    pub max_tokens: Option<u8>,
    pub output_language: Option<String>,
    pub log_file: Option<String>,
}

/// Main ErrorSolver struct
pub struct ErrorSolver {
    base_url: String,
    api_key: String,
    model: String,
    system_prompt: String,
    temperature: f32,
    max_tokens: u8,
    client: Client,
    log_file: String,
}

impl ErrorSolver {
    /// Create a new ErrorSolver instance using an ErrorSolverConfig.
    /// All fields are optional and default values or environment variables will be used if not set.
    pub fn new(config: ErrorSolverConfig) -> Self {
        dotenv::dotenv().ok();

        // Determine the base URL, defaulting if not provided
        let base_url = config
            .base_url
            .or_else(|| env::var("OPENAI_BASE_URL").ok())
            .unwrap_or_else(|| "http://127.0.0.1:1234/v1".to_string());

        // Retrieve the API key from environment variables
        let api_key = config
            .api_key
            .or_else(|| env::var("OPENAI_API_KEY").ok())
            .unwrap_or_else(|| "".to_string());

        // Set the model using the function argument, then the environment variable,
        // and finally a hardcoded default
        let model = config
            .model
            .or_else(|| env::var("OPENAI_API_MODEL").ok())
            .unwrap_or_else(|| "gpt-4".to_string());

        // Temperature defaults to 0.5
        let temperature = config.temperature.unwrap_or(0.5);

        // Max tokens defaults to 200
        let max_tokens = config.max_tokens.unwrap_or(200);

        // Output language defaults to Italiano
        let output_language = config
            .output_language
            .unwrap_or_else(|| "Italiano".to_string());

        // Log file defaults to logs/errorsolver.log
        let log_file = config
            .log_file
            .unwrap_or_else(|| "logs/errorsolver.log".to_string());

        // System prompt defaults to Rust error checker unless overridden
        let system_prompt = config
            .system_prompt
            .or_else(|| env::var("OPENAI_API_PROMPT").ok())
            .unwrap_or_else(|| {
                format!(
                    "Trova il bug e proponi la soluzione in modo molto conciso. Rispondi sempre in lingua {}",
                    output_language
                )
            });

        // Ensure the directory for the log file exists
        if let Some(parent) = Path::new(&log_file).parent() {
            create_dir_all(parent).unwrap();
        }

        Self {
            base_url,
            api_key,
            model,
            system_prompt,
            temperature,
            max_tokens,
            client: Client::new(),
            log_file,
        }
    }

    /// Simple logging method — logs to both console and file
    pub fn log(&self, level: &str, message: &str) {
        println!("{} - {}", level, message);

        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.log_file)
            .unwrap();
        writeln!(file, "{} - {}", level, message).unwrap();
    }

    /// Solve an error message or log by sending it to the AI model
    pub fn solve_from_log(&self, text: &str) -> Result<String, Box<dyn std::error::Error>> {
        let body = json!({
            "model": self.model,
            "temperature": self.temperature,
            "max_tokens": self.max_tokens,
            "messages": [
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": text}
            ]
        });

        let resp: Value = self
            .client
            .post(format!("{}/chat/completions", self.base_url))
            .header("Authorization", format!("Bearer {}", self.api_key))
            .json(&body)
            .send()?
            .json()?;

        let content = resp["choices"][0]["message"]["content"]
            .as_str()
            .unwrap_or("Nessuna risposta")
            .to_string();

        Ok(content)
    }
}
