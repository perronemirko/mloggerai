from mloggerai import ErrorSolverBuilder

logger = (
    ErrorSolverBuilder()
    .with_model("lmstudio-community/llama-3.2-3b-instruct")
    .with_output_language("inglese")
    .with_log_file("logs/custom.log")
    .build()
).logger

logger.info("Applicazione avviata")
logger.error("Errore: panic in thread principale")

