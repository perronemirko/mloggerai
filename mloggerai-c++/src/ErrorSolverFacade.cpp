#include "ErrorSolverFacade.hpp"

ErrorSolverFacade::ErrorSolverFacade(std::shared_ptr<ErrorSolver> solver)
    : solver_(std::move(solver)) {}

void ErrorSolverFacade::info(const std::string& msg)    { solver_->info(msg); }
void ErrorSolverFacade::warning(const std::string& msg) { solver_->warning(msg); }
void ErrorSolverFacade::error(const std::string& msg)   { solver_->log("ERROR", msg); }
void ErrorSolverFacade::fatal(const std::string& msg)   { solver_->fatal(msg); }
void ErrorSolverFacade::debug(const std::string& msg)   { solver_->debug(msg); }
