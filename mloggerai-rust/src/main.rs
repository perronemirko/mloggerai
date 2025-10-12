mod errorsolver;

use errorsolver::{ErrorSolver, ErrorSolverConfig};

fn main() {
    // Create an instance of ErrorSolver with custom options
    let logger = ErrorSolver::new(ErrorSolverConfig {
        // You can set only what you want — everything else has defaults
        base_url: Some("http://127.0.0.1:1234/v1".to_string()),
        api_key: Some("lm-studio".to_string()),
        log_file: Some("logs/logger.log".to_string()),
        output_language: Some("italiano".to_string()),
        temperature: Some(0.7),
        model: Some("lmstudio-community/llama-3.2-3b-instruct".to_string()),
        ..Default::default() // fill remaining fields with defaults
    });

    logger.log("INFO", "Avvio del programma di analisi errori...");
    logger.wanrn("Avvio del programma di analisi errori...");
    logger.fatal("Avvio del programma di analisi errori...");
    logger.debug("Avvio del programma di analisi errori...");
    // Example error message to analyze
    let simulated_error = "Errore: panic in thread principale";

    logger.info(&format!("Analisi in corso per: {}", simulated_error));

    match logger.error(simulated_error) {
        Ok(solution) => {
            logger.log("INFO", &format!("Soluzione AI: {}", solution));
        }
        Err(e) => {
            logger.log("ERROR", &format!("Errore durante l'analisi AI: {}", e));
        }
    }

    logger.log("INFO", "Esecuzione terminata.");
}
