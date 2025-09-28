import { ErrorSolver } from "./mloggerai.js";

const logger = new ErrorSolver().logger;

logger.info("Applicazione avviata");
logger.error("ReferenceError: x is not defined");
