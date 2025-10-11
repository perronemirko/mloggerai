from mloggerai import ErrorSolverBuilder

logger = (
    ErrorSolverBuilder()
    .with_model("lmstudio-community/llama-3.2-3b-instruct")
    .with_output_language("inglese")
    .with_log_file("logs/custom.log")
    .build()
).logger

logger.info("Applicazione avviata")
for val in [0, "a", None]:
    try:
        result = 10 / val
    except Exception as e:
        logger.error(f"Error with value: {val}", exc_info=e)