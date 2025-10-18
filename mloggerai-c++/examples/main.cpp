#include "ErrorSolver.hpp"
#include <iostream>
#include <thread>
#include <chrono>

int main() {
    try {
        // ✅ Istanzia l'ErrorSolver
        ErrorSolver solver(
            "",                      // model (vuoto → usa default)
            "logs/error_solver.log", // file di log
            "it",                    // lingua di output
            0.7,                     // temperatura
            400,                     // max token
            "",                      // prompt (vuoto → usa default)
            10 * 1024,               // max 10 KB per rotazione
            3,                       // conserva 3 backup
            false                    // disabilita SSL check (utile per test locale)
        );

        // 📘 Log di vari livelli
        solver.info("Inizializzazione del sistema completata.");
        solver.debug("Variabile di configurazione caricata correttamente.");
        solver.warning("Il file di cache non è stato trovato, verrà ricreato.");

        // 🧨 Simula un errore
        solver.log("ERROR", "Divisione per zero nella funzione computeValue().");

        // 🕓 Attendi un attimo per leggere i log in console
        std::this_thread::sleep_for(std::chrono::seconds(1));

        solver.fatal("Errore critico: impossibile continuare l'esecuzione.");
        std::cout << "\n✅ Test completato — controlla il file logs/error_solver.log\n";

    } catch (const std::exception& e) {
        std::cerr << "Eccezione catturata nel main: " << e.what() << std::endl;
    }

    return 0;
}
