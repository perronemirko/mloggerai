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
        dotenv::init(env_file.c_str());
        std::cout << "✅ File " << env_file << " caricato correttamente.\n";
    } catch (const std::exception& e) {
        std::cerr << "⚠️ Impossibile caricare " << env_file << ": " << e.what() << "\n";
    }

    // Legge variabili d'ambiente, se disponibili
    const char* api_key_env   = std::getenv("OPENAI_API_KEY");
    const char* api_url_env   = std::getenv("OPENAI_API_URL");
    const char* api_model_env = std::getenv("OPENAI_API_MODEL");
    const char* api_prompt_env = std::getenv("OPENAI_API_PROMPT");

    // Valori fallback se le environment non esistono
    std::string api_key   = api_key_env   ? api_key_env   : "inserisci_api_key";
    std::string api_url   = api_url_env   ? api_url_env   : "http://localhost:1234/v1";
    std::string api_model = api_model_env ? api_model_env : "gpt-4";
    std::string api_prompt = api_prompt_env ? api_prompt_env :
        "Trova il bug e proponi la soluzione in modo conciso.";

    std::cout << "✅ Variabili configurate:\n";
    std::cout << "   OPENAI_API_URL="   << api_url   << "\n";
    std::cout << "   OPENAI_API_MODEL=" << api_model << "\n";

    // Istanza ErrorSolver con fallback anche se le env non esistono
    ErrorSolver solver(
        api_model,          // modello AI
        "logs/app.log",     // percorso log file
        "Italiano",         // lingua output AI
        0.4,                // temperature
        150,                // max tokens
        api_prompt,         // prompt personalizzato
        2 * 1024 * 1024,    // dimensione massima log file (2 MB)
        3,                  // numero di backup
        true                // verifica SSL
    );

    // Logging di test
    solver.log("INFO", "Applicazione avviata correttamente.");
    solver.log("DEBUG", "Messaggio di debug di esempio.");
    solver.log("ERROR", "Errore di esempio che attiva AI.");

    // Chiamata diretta al solver su messaggio personalizzato
    std::string ai_solution = solver.solve_from_log("NullPointerException at MyClass.java:42");
    std::cout << "✅ Soluzione AI per il test: " << ai_solution << std::endl;

    return 0;
}
