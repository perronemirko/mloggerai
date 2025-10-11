mod errorsolver;

use errorsolver::{ErrorSolver, ErrorSolverConfig};

fn main() {
    // Create an instance of ErrorSolver with custom options
    let logger = ErrorSolver::new(ErrorSolverConfig {
        // You can set only what you want — everything else has defaults
        log_file: Some("logs/logger.log".to_string()),
        output_language: Some("italiano".to_string()),
        temperature: Some(0.7),
        model: Some("gpt-4".to_string()),
        ..Default::default() // fill remaining fields with defaults
    });

    logger.log("INFO", "Avvio del programma di analisi errori...");

    // Example error message to analyze
    let simulated_error = "Errore: panic in thread principale";

    logger.log("INFO", &format!("Analisi in corso per: {}", simulated_error));

    match logger.solve_from_log(simulated_error) {
        Ok(solution) => {
            logger.log("INFO", &format!("Soluzione AI: {}", solution));
        }
        Err(e) => {
            logger.log("ERROR", &format!("Errore durante l'analisi AI: {}", e));
        }
    }

    logger.log("INFO", "Esecuzione terminata.");
}