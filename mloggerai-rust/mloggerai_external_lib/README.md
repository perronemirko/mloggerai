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
mloggerai = "0.0.1"

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

use error_solver::ErrorSolver;

fn main() {
    let solver = ErrorSolver::new(None, "logs/logger.log", "italiano");

    solver.log("INFO", "Applicazione avviata");
    solver.log("ERROR", "Errore generico di test");
}
```
## 3️⃣ Solve log's errors via AI
```rust
use error_solver::ErrorSolver;

fn main() {
    let solver = ErrorSolver::new(None, "logs/logger.log", "italiano");

    match solver.solve_from_log("Errore: panic in thread principale") {
        Ok(solution) => println!("✅ Soluzione AI: {}", solution),
        Err(e) => eprintln!("❌ Errore AI: {}", e),
    }
}
```
## 3️⃣ Use it a personal On-Prem model using server API like Ollama llama.cpp lm-studio OpenApi 
```rust
use error_solver::ErrorSolver;

fn main() {
    // Forziamo un modello specifico invece di leggere da .env
    let solver = ErrorSolver::new(Some("<YourModel>".to_string()), "logs/custom.log", "inglese");

    solver.log("INFO", "Test con modello personalizzato");

    match solver.solve_from_log("NullPointerException at MyClass.java:42") {
        Ok(solution) => println!("AI Suggestion: {}", solution),
        Err(e) => eprintln!("Errore: {}", e),
    }
}

```
## 4️⃣ Bigger project integration
```rust
use error_solver::ErrorSolver;

pub fn run_app() {
    let solver = ErrorSolver::new(None, "logs/app.log", "italiano");

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