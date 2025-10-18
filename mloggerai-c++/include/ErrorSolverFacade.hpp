#pragma once
#include <string>
#include <memory>
#include "ErrorSolver.hpp"

class ErrorSolverFacade {
public:
    explicit ErrorSolverFacade(std::shared_ptr<ErrorSolver> solver);

    void info(const std::string& msg);
    void warning(const std::string& msg);
    void error(const std::string& msg);
    void fatal(const std::string& msg);
    void debug(const std::string& msg);

private:
    std::shared_ptr<ErrorSolver> solver_;
};
