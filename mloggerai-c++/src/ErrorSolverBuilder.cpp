#include "ErrorSolverBuilder.hpp"
#include <filesystem>
namespace fs = std::filesystem;

ErrorSolverBuilder::ErrorSolverBuilder()
    : model_("gpt-4"),
      log_file_("logs/errorsolver.log"),
      output_language_("it"),
      temperature_(0.7),
      max_tokens_(400),
      system_prompt_("Trova il bug e proponi la soluzione in modo concisa."),
      max_bytes_(1024 * 1024),
      backup_count_(3),
      verify_ssl_(true) {}

ErrorSolverBuilder& ErrorSolverBuilder::setModel(const std::string& model) { model_ = model; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setLogFile(const std::string& log_file) { log_file_ = log_file; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setOutputLanguage(const std::string& lang) { output_language_ = lang; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setTemperature(double temp) { temperature_ = temp; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setMaxTokens(int tokens) { max_tokens_ = tokens; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setSystemPrompt(const std::string& prompt) { system_prompt_ = prompt; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setMaxBytes(size_t max_bytes) { max_bytes_ = max_bytes; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setBackupCount(int count) { backup_count_ = count; return *this; }
ErrorSolverBuilder& ErrorSolverBuilder::setVerifySSL(bool verify) { verify_ssl_ = verify; return *this; }

std::shared_ptr<ErrorSolver> ErrorSolverBuilder::build() const {
    fs::create_directories(fs::path(log_file_).parent_path());
    return std::make_shared<ErrorSolver>(
        model_,
        log_file_,
        output_language_,
        temperature_,
        max_tokens_,
        system_prompt_,
        max_bytes_,
        backup_count_,
        verify_ssl_
    );
}
