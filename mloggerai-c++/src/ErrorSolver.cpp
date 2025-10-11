#include "ErrorSolver.hpp"
#include <iostream>
#include <fstream>
#include <ctime>
#include <filesystem>
#include <cstdlib>
#include <cpr/cpr.h>
#include <nlohmann/json.hpp>

namespace fs = std::filesystem;

ErrorSolver::ErrorSolver(
        const std::string& model,
        const std::string& log_file,
        const std::string& output_language,
        double temperature,
        int max_tokens,
        const std::string& system_prompt,
        size_t max_bytes,
        int backup_count,
        bool verify_ssl)
    : model_(model), log_file_(log_file), output_language_(output_language),
      temperature_(temperature), max_tokens_(max_tokens),
      system_prompt_(system_prompt), max_bytes_(max_bytes),
      backup_count_(backup_count), verify_ssl_(verify_ssl)
{
    const char* env_url   = std::getenv("OPENAI_API_URL");
    const char* env_key   = std::getenv("OPENAI_API_KEY");
    const char* env_model = std::getenv("OPENAI_API_MODEL");
    const char* env_prompt = std::getenv("OPENAI_API_PROMPT");

    base_url_ = env_url ? env_url : "http://localhost:1234/v1";
    api_key_  = env_key   ? env_key   : "";

    if (model_.empty() && env_model) {
        model_ = env_model;
    }

    if (system_prompt_.empty()) {
        system_prompt_ = env_prompt ? env_prompt :
            "Trova il bug e proponi la soluzione in modo conciso. Rispondi sempre in lingua " + output_language_;
    }

    fs::create_directories(fs::path(log_file_).parent_path());
}

void ErrorSolver::log(const std::string& level, const std::string& message) {
    std::string timestamp = current_time();
    std::string log_msg = timestamp + " - " + level + " - " + message;

    std::cout << log_msg << std::endl;

    rotate_logs();

    std::ofstream ofs(log_file_, std::ios::app);
    if (ofs.is_open()) {
        ofs << log_msg << std::endl;
        ofs.close();
    }

    if (level == "ERROR") {
        std::string solution = solve_from_log(message);
        std::string combined = "🧠💡 Soluzione AI: " + solution;
        log("DEBUG", combined);
    }
}

std::string ErrorSolver::current_time() const {
    std::time_t t = std::time(nullptr);
    char buf[64];
    std::strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", std::localtime(&t));
    return buf;
}

std::string ErrorSolver::solve_from_log(const std::string& text) {
    try {
        nlohmann::json messages = {
            { {"role", "system"}, {"content", system_prompt_} },
            { {"role", "user"}, {"content", text} }
        };

        nlohmann::json payload = {
            {"model", model_},
            {"temperature", temperature_},
            {"max_tokens", max_tokens_},
            {"messages", messages}
        };

        cpr::Session session;
        session.SetUrl(cpr::Url{base_url_ + "/chat/completions"});
        session.SetHeader({{"Authorization", "Bearer " + api_key_}, {"Content-Type", "application/json"}});
        session.SetBody(cpr::Body{payload.dump()});
        session.SetVerifySsl(verify_ssl_);

        cpr::Response r = session.Post();

        if (r.error) {
            return "Errore AI (cpr): " + r.error.message;
        }

        if (r.status_code == 200) {
            auto json_resp = nlohmann::json::parse(r.text);
            return json_resp["choices"][0]["message"]["content"].get<std::string>();
        } else {
            return "Errore AI: HTTP " + std::to_string(r.status_code) + " - " + r.text;
        }
    } catch (const std::exception& e) {
        return std::string("Errore AI: ") + e.what();
    }
}

void ErrorSolver::rotate_logs() {
    if (!fs::exists(log_file_)) return;

    size_t file_size = fs::file_size(log_file_);
    if (file_size < max_bytes_) return;

    for (int i = backup_count_ - 1; i >= 0; --i) {
        fs::path old_file = (i == 0) ? log_file_ : log_file_ + "." + std::to_string(i);
        fs::path new_file = log_file_ + "." + std::to_string(i + 1);

        if (fs::exists(old_file)) {
            if (i + 1 >= backup_count_) {
                fs::remove(old_file);
            } else {
                fs::rename(old_file, new_file);
            }
        }
    }
}
