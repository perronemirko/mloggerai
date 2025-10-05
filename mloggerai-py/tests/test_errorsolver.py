from mloggerai.errorsolver import ErrorSolver
from dotenv import load_dotenv
load_dotenv()  # Carica le variabili da .env
def main():
    solver = ErrorSolver()
    logger = solver.logger

    try:
        1 / 0
    except Exception as e:
        logger.error("Errore catturato", exc_info=e)

if __name__ == "__main__":
    main()