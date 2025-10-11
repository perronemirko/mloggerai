#pragma once
#include <string>

class ErrorSolver {
public:
    ErrorSolver(
        const std::string& model = "",
        const std::string& log_file = "logs/errorsolver.log",
        const std::string& output_language = "Italiano",
        double temperature = 0.5,
        int max_tokens = 200,
        const std::string& system_prompt = "",
        size_t max_bytes = 5 * 1024 * 1024, // 5 MB
        int backup_count = 5,
        bool verify_ssl = true
    );

    void log(const std::string& level, const std::string& message);
    std::string solve_from_log(const std::string& text);

private:
    std::string current_time() const;
    void rotate_logs();

    std::string base_url_;
    std::string api_key_;
    std::string model_;
    std::string system_prompt_;
    std::string output_language_;
    std::string log_file_;
    double temperature_;
    int max_tokens_;
    size_t max_bytes_;
    int backup_count_;
    bool verify_ssl_;
};
