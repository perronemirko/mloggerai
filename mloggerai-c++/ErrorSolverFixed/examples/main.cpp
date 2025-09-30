#include "ErrorSolver.hpp"
#include <cstdlib>
#include <iostream>
#include <laserpants/dotenv/dotenv.h>

int main(int argc, char* argv[]) {
    // Carica il file .env
    std::string env_file = ".env";
    if (argc > 1) {
        env_file = argv[1];
    }

    try {
        // dotenv::init();
        dotenv::init(env_file.c_str());
        std::cout << "✅ File " << env_file << " caricato correttamente.\n";
    } catch (const std::exception& e) {
        std::cerr << "⚠️ Impossibile caricare " << env_file << ": " << e.what() << "\n";
    }

    const char* api_key   = std::getenv("OPENAI_API_KEY");
    const char* api_url   = std::getenv("OPENAI_API_URL");
    const char* api_model = std::getenv("OPENAI_API_MODEL");

    if (!api_key || std::string(api_key).empty()) {
        std::cerr << "❌ Variabile OPENAI_API_KEY non trovata!\n";
        return 1;
    }
    if (!api_url || std::string(api_url).empty()) {
        std::cerr << "❌ Variabile OPENAI_API_URL non trovata! (es: https://api.openai.com/v1)\n";
        return 1;
    }
    if (!api_model || std::string(api_model).empty()) {
        std::cerr << "❌ Variabile OPENAI_API_MODEL non trovata! (es: gpt-4o-mini)\n";
        return 1;
    }

    std::cout << "✅ Variabili caricate correttamente:\n";
    std::cout << "   OPENAI_API_URL="   << api_url   << "\n";
    std::cout << "   OPENAI_API_MODEL=" << api_model << "\n";

    // Istanza ErrorSolver (SSL attivo per default)
    ErrorSolver solver(api_model);
    solver.log("INFO", "Applicazione avviata");
    solver.log("ERROR", "Questo è un errore di esempio");

    return 0;
}