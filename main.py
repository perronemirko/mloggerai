from ailogger.errosolver import ErrorSolver  # Assicurati che il file si chiami ErrorSolver.py

def main():
    solver = ErrorSolver(model="<YOUR_MODEL_NAME>", output_language="inglese")
    logger = solver.logger

    try:
        1 / 0
    except Exception as e:
        logger.error("Errore catturato", exc_info=e)

if __name__ == "__main__":
    main()