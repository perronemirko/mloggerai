# mloggerai

Rust library for **analyzing logs** and obtaining AI solutions via the OpenAI Client (or compatible ones).
It allows logging messages to the console and files, and sending errors to the AI model to receive automatic correction suggestions.
---

## ✨ ✨ Features

- Logging to console and files.
- Integration with OpenAI API (chat completions).
- Automatic suggestions in a configurable language (default: Italian).
- Easy to include as a crate in other projects.

---

## 📦 Installazione

Add to your `Cargo.toml`:

```toml
[dependencies]
mloggerai = "0.0.2"

toml
[dependencies]
mloggerai = { path = "../mloggerai-rust" }

```
## ⚙️ Configurazione

Create .env file in the project root:
```bash
OPENAI_API_URL=https://api.openai.com/v1
OPENAI_API_KEY=sk-xxxxxxx
OPENAI_API_MODEL=gpt-4
OPENAI_API_PROMPT=Trova il bug e proponi la soluzione in modo conciso.
```

### Examples
## 1️⃣ Simple Logging 
```rust

use mloggerai::errorsolver::{ErrorSolver, ErrorSolverConfig};

fn main() {
    let solver = ErrorSolver::new(ErrorSolverConfig {
        log_file: Some("logs/logger.log".to_string()),
        output_language: Some("italiano".to_string()),
        ..Default::default()
    });

    solver.log("INFO", "Applicazione avviata");
    solver.log("ERROR", "Errore generico di test");
}

```
## 3️⃣ Solve log's errors via AI
```rust
use mloggerai::errorsolver::{ErrorSolver, ErrorSolverConfig};

fn main() {
    let solver = ErrorSolver::new(ErrorSolverConfig {
        output_language: Some("italiano".to_string()),
        ..Default::default()
    });

    match solver.solve_from_log("Errore: panic in thread principale") {
        Ok(solution) => println!("✅ Soluzione AI: {}", solution),
        Err(e) => eprintln!("❌ Errore AI: {}", e),
    }
}

```
## 3️⃣ Use it a personal On-Prem model using server API like Ollama llama.cpp lm-studio OpenApi 
```rust
use mloggerai::errorsolver::{ErrorSolver, ErrorSolverConfig};

fn main() {
    let solver = ErrorSolver::new(ErrorSolverConfig {
        base_url: Some("http://127.0.0.1:11434/v1".to_string()),
        model: Some("llama3".to_string()),
        output_language: Some("inglese".to_string()),
        log_file: Some("logs/custom.log".to_string()),
        ..Default::default()
    });

    solver.log("INFO", "Test con modello personalizzato");

    match solver.solve_from_log("NullPointerException at MyClass.java:42") {
        Ok(solution) => println!("💡 AI Suggestion: {}", solution),
        Err(e) => eprintln!("❌ Errore: {}", e),
    }
}


```
## 4️⃣ Bigger project integration
```rust
use mloggerai::errorsolver::{ErrorSolver, ErrorSolverConfig};

pub fn run_app() {
    let solver = ErrorSolver::new(ErrorSolverConfig {
        log_file: Some("logs/app.log".to_string()),
        ..Default::default()
    });

    if let Err(e) = do_something() {
        solver.log("ERROR", &format!("Errore riscontrato: {}", e));

        if let Ok(solution) = solver.solve_from_log(&format!("{}", e)) {
            solver.log("INFO", &format!("Soluzione AI: {}", solution));
        }
    }
}

fn do_something() -> Result<(), &'static str> {
    Err("Divisione per zero")
}

```