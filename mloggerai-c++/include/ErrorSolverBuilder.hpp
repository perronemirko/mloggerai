#pragma once
#include <string>
#include <cpr/cpr.h>

class ErrorSolverBuilder {
public:
    // Costruttore base, legge le env o fallback
    ErrorSolverBuilder();

    // Builder/fluent interface
    ErrorSolverBuilder& setModel(const std::string& model);
    ErrorSolverBuilder& setLogFile(const std::string& log_file);
    ErrorSolverBuilder& setOutputLanguage(const std::string& lang);
    ErrorSolverBuilder& setTemperature(double temp);
    ErrorSolverBuilder& setMaxTokens(int tokens);
    ErrorSolverBuilder& setSystemPrompt(const std::string& prompt);
    ErrorSolverBuilder& setMaxBytes(size_t max_bytes);
    ErrorSolverBuilder& setBackupCount(int count);
    ErrorSolverBuilder& setVerifySSL(bool verify);

    void log(const std::string& level, const std::string& message);
    std::string solve_from_log(const std::string& text);

private:
    std::string model_;
    std::string base_url_;
    std::string api_key_;
    std::string log_file_;
    std::string output_language_;
    std::string system_prompt_;
    double temperature_;
    int max_tokens_;
    size_t max_bytes_;
    int backup_count_;
    bool verify_ssl_;

    std::string current_time();
    void rotate_logs();
};
