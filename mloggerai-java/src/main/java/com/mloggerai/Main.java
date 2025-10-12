package com.mloggerai;

/**
 * Main class to demonstrate the usage of {@link ErrorSolver}.
 * <p>
 * This class initializes the ErrorSolver, sends a sample error message
 * to the AI model, prints the response, and properly shuts down resources.
 */
public class Main {

    /**
     * Entry point of the application.
     * <p>
     * Initializes the ErrorSolver, sends a test error message to the AI,
     * prints the AI's solution, and shuts down resources.
     *
     * @param args Command-line arguments (not used).
     */
    public static void main(String[] args) {
        // Initialize ErrorSolver
        ErrorSolver logger = new ErrorSolver();

        // Example message to analyze
        String errorMessage = "NullPointerException at line 42 in UserService.java";

        System.out.println("➡️ Sending error to AI solver...");
        String solution = logger.error(errorMessage);

        System.out.println("✅ AI Response:");
        System.out.println(solution);

        // Close resources
        logger.shutdown();
    }
}
