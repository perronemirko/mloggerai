#pragma once

#include <string>

class ErrorSolver {
public:
    ErrorSolver(const std::string& model = "",
                const std::string& log_file = "logs/errors.log",
                const std::string& output_language = "it",
                double temperature = 0.7,
                int max_tokens = 400,
                const std::string& system_prompt = "",
                size_t max_bytes = 1024 * 1024,
                int backup_count = 5,
                bool verify_ssl = true);

    void info(const std::string& message);
    void debug(const std::string& message);
    void warning(const std::string& message);
    void fatal(const std::string& message);
    void log(const std::string& level, const std::string& message);

private:
    std::string current_time();
    std::string solve_from_log(const std::string& text);
    void rotate_logs();

    std::string base_url_;
    std::string api_key_;
    std::string model_;
    std::string log_file_;
    std::string output_language_;
    std::string system_prompt_;

    double temperature_;
    int max_tokens_;
    size_t max_bytes_;
    int backup_count_;
    bool verify_ssl_;

    bool in_ai_response_ = false;
};
