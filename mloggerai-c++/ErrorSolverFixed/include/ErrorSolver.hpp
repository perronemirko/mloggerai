#pragma once
#include <string>

class ErrorSolver {
public:
    ErrorSolver(const std::string& model = "",
                const std::string& log_file = "logs/logger.log",
                const std::string& output_language = "italiano",
                size_t max_bytes = 5'000'000,
                int backup_count = 3,
                bool verify_ssl = true);   // 👈 nuovo flag

    void log(const std::string& level, const std::string& message);

private:
    std::string model_;
    std::string base_url_;
    std::string api_key_;
    std::string log_file_;
    std::string output_language_;
    size_t max_bytes_;
    int backup_count_;
    bool verify_ssl_;   // 👈 memorizzato nel costruttore

    std::string current_time();
    std::string solve_from_log(const std::string& text);
    void rotate_logs();
};

