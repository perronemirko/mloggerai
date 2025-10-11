#include "ErrorSolverBuilder.hpp"
#include <filesystem>
#include <fstream>
#include <iostream>
#include <cstdlib>
#include <ctime>
#include <nlohmann/json.hpp>

namespace fs = std::filesystem;

// Costruttore di default
ErrorSolverBuilder::ErrorSolverBuilder()
    : model_("gpt-4"),
      log_file_("logs/errorsolverBuilder.log"),
      output_language_("Italiano"),
      temperature_(0.5),
      max_tokens_(200),
      system_prompt_("Trova il bug e proponi la soluzione in modo conciso."),
      max_bytes_(2*1024*1024),
      backup_count_(3),
      verify_ssl_(true)
{
    const char* env_url   = std::getenv("OPENAI_API_URL");
    const char* env_key   = std::getenv("OPENAI_API_KEY");
    const char* env_model = std::getenv("OPENAI_API_MODEL");
    const char* env_prompt = std::getenv("OPENAI_API_PROMPT");

    base_url_ = env_url ? env_url : "http://localhost:1234/v1";
    api_key_ = env_key ? env_key : "inserisci_api_key";
    if (env_model) model_ = env_model;
    if (env_prompt) system_prompt_ = env_prompt;

    fs::create_directories(fs::path(log_file_).parent_path());
}

// ===== Builder methods =====
ErrorSolverBuilder& ErrorSolverBuilder::setModel(const std::string& model) { model_ = model; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setLogFile(const std::string& log_file) { log_file_ = log_file; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setOutputLanguage(const std::string& lang) { output_language_ = lang; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setTemperature(double temp) { temperature_ = temp; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setMaxTokens(int tokens) { max_tokens_ = tokens; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setSystemPrompt(const std::string& prompt) { system_prompt_ = prompt; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setMaxBytes(size_t max_bytes) { max_bytes_ = max_bytes; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setBackupCount(int count) { backup_count_ = count; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setVerifySSL(bool verify) { verify_ssl_ = verify; return *this; }

// ===== Logging =====
void ErrorSolverBuilder::log(const std::string& level, const std::string& message) {
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
        log("DEBUG", "🧠💡 Soluzione AI: " + solution);
    }
}

std::string ErrorSolverBuilder::current_time() {
    std::time_t t = std::time(nullptr);
    char buf[64];
    std::strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", std::localtime(&t));
    return buf;
}

// ===== AI solver =====
std::string ErrorSolverBuilder::solve_from_log(const std::string& text) {
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
        if (!verify_ssl_) session.SetVerifySsl(false);

        cpr::Response r = session.Post();
        if (r.error) return "Errore AI (cpr): " + r.error.message;
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

// ===== Rotate logs =====
void ErrorSolverBuilder::rotate_logs() {
    if (!fs::exists(log_file_)) return;
    size_t file_size = fs::file_size(log_file_);
    if (file_size < max_bytes_) return;

    for (int i = backup_count_ - 1; i >= 0; --i) {
        fs::path old_file = (i == 0) ? log_file_ : log_file_ + "." + std::to_string(i);
        fs::path new_file = log_file_ + "." + std::to_string(i + 1);
        if (fs::exists(old_file)) {
            if (i + 1 >= backup_count_) fs::remove(old_file);
            else fs::rename(old_file, new_file);
        }
    }
}
