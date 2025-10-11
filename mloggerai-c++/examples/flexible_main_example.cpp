#include "ErrorSolver.hpp"
#include <iostream>

int main() {
    // Tutti i parametri sono opzionali: usa env o fallback
    ErrorSolver solver;  

    solver.log("INFO", "Applicazione avviata.");
    solver.log("ERROR", "Errore di esempio");

    std::string solution = solver.solve_from_log("NullPointerException at MyClass.java:42");
    std::cout << "Soluzione AI: " << solution << std::endl;

    return 0;
}
