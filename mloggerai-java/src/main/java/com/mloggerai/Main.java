package com.mloggerai;

public class Main {
    public static void main(String[] args) {
        // Inizializza ErrorSolver
        ErrorSolver solver = new ErrorSolver();

        // Messaggio di esempio da analizzare
        String errorMessage = "NullPointerException at line 42 in UserService.java";

        System.out.println("➡️ Inviando errore al solver AI...");
        String solution = solver.solveFromLog(errorMessage);

        System.out.println("✅ Risposta AI:");
        System.out.println(solution);

        // Chiudi risorse
        solver.shutdown();
    }
}
