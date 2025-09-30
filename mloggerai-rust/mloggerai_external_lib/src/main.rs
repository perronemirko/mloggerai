
use error_solver::ErrorSolver;

fn main() {
    let solver = ErrorSolver::new(None, "logs/logger.log", "italiano");

    solver.log("INFO", "Esecuzione demo libreria");

    match solver.solve_from_log("Errore: panic in thread principale") {
        Ok(solution) => solver.log("INFO", &format!("Soluzione AI: {}", solution)),
        Err(e) => solver.log("ERROR", &format!("Errore AI: {}", e)),
    }
}
