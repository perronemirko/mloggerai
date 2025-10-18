#include "ErrorSolverBuilder.hpp"
#include "ErrorSolverFacade.hpp"
#include <iostream>

int main() {
    auto solver = ErrorSolverBuilder()
        .setModel("gpt-4")
        .setLogFile("logs/facade_test.log")
        .setBackupCount(3)
        .setMaxBytes(10 * 1024)
        .setVerifySSL(false)
        .build();

    ErrorSolverFacade logger(solver);

    logger.info("Sistema avviato correttamente.");
    logger.warning("Memoria disponibile bassa.");
    logger.error("Divisione per zero in modulo core.");
    logger.fatal("Errore critico non recuperabile.");
    logger.debug("Log di debug attivo.");

    std::cout << "\n✅ Test completato. Controlla i log in logs/facade_test.log\n";
    return 0;
}
