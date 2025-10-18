#include "ErrorSolverBuilder.hpp"
#include "ErrorSolverFacade.hpp"
#include <iostream>
#include <thread>
#include <chrono>

int main() {
    try {
        // =====================================================
        // 🏗️ Costruzione tramite il Builder (fluent interface)
        // =====================================================
        auto solver = ErrorSolverBuilder()
            .setModel("gpt-4")
            .setLogFile("logs/facade_test.log")
            .setOutputLanguage("it")
            .setTemperature(0.6)
            .setMaxTokens(300)
            .setSystemPrompt("Analizza l'errore e spiega la soluzione in modo conciso.")
            .setMaxBytes(10 * 1024) // rotazione ogni 10 KB
            .setBackupCount(3)
            .setVerifySSL(false)
            .build();

        // =====================================================
        // 🧱 Creazione della Facade
        // =====================================================
        ErrorSolverFacade logger(solver);

        // =====================================================
        // 🧠 Uso della logger (interfaccia semplificata)
        // =====================================================
        logger.info("Avvio del sistema completato.");
        logger.debug("Inizializzazione dei moduli core.");
        logger.warning("File di configurazione mancante, uso valori di default.");
        logger.error("Eccezione std::out_of_range in Parser::extractToken().");
        logger.fatal("Errore irreversibile: impossibile recuperare memoria.");

        std::this_thread::sleep_for(std::chrono::milliseconds(200));

        std::cout << "\n✅ Test completato con successo!\n"
                  << "   Controlla i log in 'logs/facade_test.log'\n";

    } catch (const std::exception& e) {
        std::cerr << "❌ Eccezione catturata nel main: " << e.what() << std::endl;
        return 1;
    }

    return 0;
}
