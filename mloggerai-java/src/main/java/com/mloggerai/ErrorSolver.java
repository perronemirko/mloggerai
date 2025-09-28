package com.mloggerai;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
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
    private final CloseableHttpClient httpClient;
    private final ExecutorService executor;

    public ErrorSolver(String model, String logFile, Level logLevel, String outputLanguage) {
        // Carica variabili da .env
        Dotenv dotenv = null;
        try {
            dotenv = Dotenv.load();
        } catch (Exception e) {
            System.err.println("⚠️ Could not load .env file: " + e.getMessage());
        }

        this.apiKey = (dotenv != null) ? dotenv.get("OPENAI_API_KEY", "") : "";
        this.baseUrl = (dotenv != null) ? dotenv.get("OPENAI_API_URL", "http://localhost:1234/v1") : "http://localhost:1234/v1";
        this.model = (model != null) ? model : ((dotenv != null) ? dotenv.get("OPENAI_API_MODEL", "gpt-4.1-mini") : "gpt-4.1-mini");
        this.outputLanguage = (outputLanguage != null) ? outputLanguage : "italiano";
        this.systemPrompt = (dotenv != null) ? dotenv.get("OPENAI_API_PROMPT", "Trova il bug e proponi la soluzione in modo molto conciso.")
                : "Trova il bug e proponi la soluzione in modo molto conciso.";

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
            logger.warning("Impossibile scrivere log su file: " + e.getMessage());
        }

        attachAIHandler();
    }

    public ErrorSolver() {
        this(null, "logs/logger.log", Level.ALL, "italiano");
    }

    private void attachAIHandler() {
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.SEVERE.intValue() && !apiKey.isEmpty()) {
                    String msg = record.getMessage();
                    solveFromLogAsync(msg)
                            .thenAccept(solution -> logger.info("📘 Soluzione AI: " + solution))
                            .exceptionally(e -> {
                                logger.warning("Errore AI interno: " + e.getMessage());
                                return null;
                            });
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        });
    }

    private JSONObject buildRequest(String text) {
        return new JSONObject()
                .put("model", model)
                .put("temperature", 0.3)
                .put("max_tokens", 150)
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system")
                                .put("content", systemPrompt + ". Rispondi sempre in lingua " + outputLanguage))
                        .put(new JSONObject().put("role", "user").put("content", text))
                );
    }

    // 🔹 Sincrono
    public String solveFromLog(String text) {
        if (apiKey.isEmpty()) {
            logger.warning("OPENAI_API_KEY is missing. Skipping AI call.");
            return "API key missing.";
        }

        HttpPost request = new HttpPost(baseUrl + "/chat/completions");
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Authorization", "Bearer " + apiKey);
        request.setEntity(new StringEntity(buildRequest(text).toString(), ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            if (statusCode != 200) {
                return "Errore API: status " + statusCode;
            }
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            JSONObject jsonResponse = new JSONObject(body);
            return jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();
        } catch (IOException | ParseException e) {
            logger.warning("Errore AI durante la richiesta: " + e.getMessage());
            return "Errore AI: " + e.getMessage();
        }
    }

    // 🔹 Asincrono
    public CompletableFuture<String> solveFromLogAsync(String text) {
        return CompletableFuture.supplyAsync(() -> solveFromLog(text), executor);
    }

    public Logger getLogger() {
        return logger;
    }

    public void shutdown() {
        try {
            httpClient.close();
        } catch (IOException ignored) {
        }
        executor.shutdown();
    }
}
