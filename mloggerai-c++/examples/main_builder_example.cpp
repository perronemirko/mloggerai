#include "ErrorSolverBuilder.hpp"
#include <iostream>

int main() {
    ErrorSolverBuilder solver;

    // Configurazione fluente
    solver.
    setModel("gpt-4")
          .setLogFile("logs/app.log")
          .setOutputLanguage("Italiano")
          .setTemperature(0.3)
          .setMaxTokens(150)
          .setSystemPrompt("Trova il bug e proponi la soluzione in modo conciso.")
          .setMaxBytes(2*1024*1024)
          .setBackupCount(3)
          .setVerifySSL(true);

    solver.log("INFO", "Applicazione avviata.");
    solver.log("ERROR", "Errore di esempio");

    std::string solution = solver.solve_from_log("NullPointerException at MyClass.java:42");
    std::cout << "✅ Soluzione AI: " << solution << std::endl;

    return 0;
}
