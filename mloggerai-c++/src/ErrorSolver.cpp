#include "ErrorSolver.hpp"
#include <filesystem>
#include <fstream>
#include <iostream>
#include <cstdlib>
#include <ctime>
#include <nlohmann/json.hpp>
#include <cpr/cpr.h>

namespace fs = std::filesystem;

ErrorSolver::ErrorSolver(const std::string& model,
                         const std::string& log_file,
                         const std::string& output_language,
                         double temperature,
                         int max_tokens,
                         const std::string& system_prompt,
                         size_t max_bytes,
                         int backup_count,
                         bool verify_ssl)
    : log_file_(log_file),
      output_language_(output_language),
      temperature_(temperature),
      max_tokens_(max_tokens),
      max_bytes_(max_bytes),
      backup_count_(backup_count),
      verify_ssl_(verify_ssl)
{
    const char* env_url    = std::getenv("OPENAI_API_URL");
    const char* env_key    = std::getenv("OPENAI_API_KEY");
    const char* env_model  = std::getenv("OPENAI_API_MODEL");
    const char* env_prompt = std::getenv("OPENAI_API_PROMPT");

    base_url_  = env_url ? env_url : "http://localhost:1234/v1";
    api_key_   = env_key ? env_key : "inserisci_api_key";
    model_     = !model.empty() ? model : (env_model ? env_model : "gpt-4");
    system_prompt_ = !system_prompt.empty() ? system_prompt :
                     (env_prompt ? env_prompt :
                     "Trova il bug e proponi la soluzione in modo concisa.");

    // Crea la directory del log se esiste un percorso
    auto log_dir = fs::path(log_file_).parent_path();
    if (!log_dir.empty())
        fs::create_directories(log_dir);
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

    // Evita la ricorsione infinita
    if (level == "ERROR" && !in_ai_response_) {
        in_ai_response_ = true;
        std::string solution = solve_from_log(message);
        log("DEBUG", "🧠💡 Soluzione AI: " + solution);
        in_ai_response_ = false;
    }
}

std::string ErrorSolver::current_time() {
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
        session.SetHeader({
            {"Authorization", "Bearer " + api_key_},
            {"Content-Type", "application/json"}
        });
        session.SetBody(cpr::Body{payload.dump()});
        if (!verify_ssl_) session.SetVerifySsl(false);

        cpr::Response r = session.Post();

        if (r.error.code != cpr::ErrorCode::OK)
            return "Errore AI (cpr): " + r.error.message;

        if (r.status_code != 200)
            return "Errore AI: HTTP " + std::to_string(r.status_code) + " - " + r.text;

        auto json_resp = nlohmann::json::parse(r.text, nullptr, false);
        if (json_resp.is_discarded())
            return "Errore AI: risposta JSON non valida.";

        if (!json_resp.contains("choices") || json_resp["choices"].empty())
            return "Errore AI: risposta vuota.";

        auto content = json_resp["choices"][0]["message"]["content"];
        if (!content.is_string())
            return "Errore AI: formato inatteso.";

        return content.get<std::string>();
    }
    catch (const std::exception& e) {
        return std::string("Errore AI: ") + e.what();
    }
}

void ErrorSolver::rotate_logs() {
    if (!fs::exists(log_file_)) return;
    if (backup_count_ <= 0) return;

    size_t file_size = fs::file_size(log_file_);
    if (file_size < max_bytes_) return;

    for (int i = backup_count_ - 1; i >= 0; --i) {
        fs::path old_file = (i == 0) ? log_file_ : log_file_ + "." + std::to_string(i);
        fs::path new_file = log_file_ + "." + std::to_string(i + 1);

        if (fs::exists(old_file)) {
            try {
                if (i + 1 >= backup_count_)
                    fs::remove(old_file);
                else
                    fs::rename(old_file, new_file);
            }
            catch (const fs::filesystem_error& e) {
                std::cerr << "Errore rotazione log: " << e.what() << std::endl;
            }
        }
    }
}

void ErrorSolver::info(const std::string& message) {
    log("INFO", message);
}
void ErrorSolver::debug(const std::string& message) {
    log("DEBUG", message);
}
void ErrorSolver::warning(const std::string& message) {
    log("WARNING", message);
}
void ErrorSolver::fatal(const std::string& message) {
    log("FATAL", message);
}
