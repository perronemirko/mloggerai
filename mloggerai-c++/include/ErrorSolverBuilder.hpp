#pragma once
#include <string>
#include <memory>
#include "ErrorSolver.hpp"

class ErrorSolverBuilder {
public:
    ErrorSolverBuilder();

    // Metodi fluent per la configurazione
    ErrorSolverBuilder& setModel(const std::string& model);
    ErrorSolverBuilder& setLogFile(const std::string& log_file);
    ErrorSolverBuilder& setOutputLanguage(const std::string& lang);
    ErrorSolverBuilder& setTemperature(double temp);
    ErrorSolverBuilder& setMaxTokens(int tokens);
    ErrorSolverBuilder& setSystemPrompt(const std::string& prompt);
    ErrorSolverBuilder& setMaxBytes(size_t max_bytes);
    ErrorSolverBuilder& setBackupCount(int count);
    ErrorSolverBuilder& setVerifySSL(bool verify);

    // Metodo finale: costruisce l'oggetto ErrorSolver
    std::shared_ptr<ErrorSolver> build() const;

private:
    std::string model_;
    std::string log_file_;
    std::string output_language_;
    double temperature_;
    int max_tokens_;
    std::string system_prompt_;
    size_t max_bytes_;
    int backup_count_;
    bool verify_ssl_;
};
