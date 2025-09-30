use std::env;
use std::fs::{create_dir_all, OpenOptions};
use std::io::Write;
use std::path::Path;

use reqwest::blocking::Client;
use serde_json::{json, Value};

pub struct ErrorSolver {
    model: String,
    output_language: String,
    client: Client,
    log_file: String,
}

impl ErrorSolver {
    pub fn new(model: Option<String>, log_file: &str, output_language: &str) -> Self {
        dotenv::dotenv().ok();

        let model = model
            .or_else(|| env::var("OPENAI_API_MODEL").ok())
            .unwrap_or_else(|| "gpt-4".to_string());
        let output_language = output_language.to_string();

        // Assicurati che la cartella del log esista
        let log_path = Path::new(log_file);
        if let Some(parent) = log_path.parent() {
            create_dir_all(parent).unwrap();
        }

        Self {
            model,
            output_language,
            client: Client::new(),
            log_file: log_file.to_string(),
        }
    }

    pub fn log(&self, level: &str, message: &str) {
        // Log su console
        println!("{} - {}", level, message);

        // Log su file
        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.log_file)
            .unwrap();
        writeln!(file, "{} - {}", level, message).unwrap();
    }

    pub fn solve_from_log(&self, text: &str) -> Result<String, Box<dyn std::error::Error>> {
        let base_url = env::var("OPENAI_API_URL")
            .unwrap_or_else(|_| "http://localhost:1234/v1".to_string());
        let api_key = env::var("OPENAI_API_KEY").unwrap_or_default();
        let system_prompt = env::var("OPENAI_API_PROMPT").unwrap_or_else(|_| {
            format!(
                "Trova il bug e proponi la soluzione in modo molto conciso. Rispondi sempre in lingua {}",
                self.output_language
            )
        });

        let body = json!({
            "model": self.model,
            "temperature": 0.3,
            "max_tokens": 150,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": text}
            ]
        });

        let resp: Value = self.client.post(format!("{}/chat/completions", base_url))
            .header("Authorization", format!("Bearer {}", api_key))
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
