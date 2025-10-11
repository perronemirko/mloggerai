package com.mloggerai;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.logging.*;

public class ErrorSolver {

    private final Logger logger;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String outputLanguage;
    private final String systemPrompt;
    private final double temperature;
    private final int max_tokens;
    private final CloseableHttpClient httpClient;
    private final ExecutorService executor;

    public ErrorSolver(String baseUrl,
                       String model,
                       String apiKey,
                       double temperature,
                       int max_tokens,
                       String logFile,
                       Level logLevel,
                       String outputLanguage) {

        // Load variables from .env
        Dotenv dotenv = null;
        try {
            dotenv = Dotenv.load();
        } catch (Exception e) {
            System.err.println("⚠️ Could not load .env file: " + e.getMessage());
        }

        this.apiKey = (apiKey != null) ? apiKey :
                ((dotenv != null) ? dotenv.get("OPENAI_API_KEY", "lm-studio") : "lm-studio");

        this.baseUrl = (baseUrl != null) ? baseUrl :
                ((dotenv != null) ? dotenv.get("OPENAI_API_URL", "http://localhost:1234/v1") : "http://localhost:1234/v1");

        this.model = (model != null) ? model :
                ((dotenv != null) ? dotenv.get("OPENAI_API_MODEL", "lmstudio-community/llama-3.2-3b-instruct")
                        : "lmstudio-community/llama-3.2-3b-instruct");

        this.temperature = (temperature != 0) ? temperature : 0.3;
        this.max_tokens = (max_tokens != 0) ? max_tokens : 180;
        this.outputLanguage = (outputLanguage != null) ? outputLanguage : "English";
        this.systemPrompt = (dotenv != null)
                ? dotenv.get("OPENAI_API_PROMPT", "Find the bug and propose a concise solution provide  one code example.")
                : "Find the bug and propose a concise solution provide one code example.";

        this.logger = Logger.getLogger("AppLogger");
        this.logger.setLevel(logLevel);

        this.executor = Executors.newFixedThreadPool(4);
        this.httpClient = HttpClients.createDefault();

        // Setup file logger
        try {
            Files.createDirectories(Paths.get(logFile).getParent());
            FileHandler fh = new FileHandler(logFile, true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
        } catch (IOException e) {
            logger.warning("Unable to write logs to file: " + e.getMessage());
        }

        attachAIHandler();
    }

    public ErrorSolver() {
        this(null, null, null, 0, 0, "logs/logger.log", Level.ALL, "English");
    }

    private void attachAIHandler() {
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.SEVERE.intValue() && !apiKey.isEmpty()) {
                    String msg = record.getMessage();
                    solveFromLogAsync(msg)
                            .thenAccept(solution -> logger.info("\uD83E\uDDE0✅  AI Solution: " + solution))
                            .exceptionally(e -> {
                                logger.warning("Internal AI error: " + e.getMessage());
                                return null;
                            });
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        });
    }

    private JSONObject buildRequest(String text) {
        return new JSONObject()
                .put("model", model)
                .put("temperature", temperature)
                .put("max_tokens", max_tokens)
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system")
                                .put("content", systemPrompt + ". Always respond in " + outputLanguage))
                        .put(new JSONObject().put("role", "user").put("content", text))
                );
    }

    // 🔹 Synchronous
    public String solveFromLog(String text) {
        if (apiKey.isEmpty()) {
            logger.warning("OPENAI_API_KEY is missing. Skipping AI call.");
            return "API key missing.";
        }

        HttpPost request = new HttpPost(baseUrl + "/chat/completions");
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Authorization", "Bearer " + apiKey);
        request.setEntity(new StringEntity(buildRequest(text).toString(), ContentType.APPLICATION_JSON));

        try {
            return httpClient.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode != 200) {
                    return "API Error: status " + statusCode;
                }

                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                JSONObject jsonResponse = new JSONObject(body);
                return "🧠  " +jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim();
            });
        } catch (IOException e) {
            logger.warning("\uD83E\uDDE0 AI request error: " + e.getMessage());
            return "\uD83E\uDDE0 AI Error: " + e.getMessage();
        }
    }

    // 🔹 Asynchronous
    public CompletableFuture<String> solveFromLogAsync(String text) {
        return CompletableFuture.supplyAsync(() -> solveFromLog(text), executor);
    }

    public Logger getLogger() {
        return logger;
    }

    public void shutdown() {
        try {
            httpClient.close();
        } catch (IOException ignored) {}
        executor.shutdown();
    }
}
