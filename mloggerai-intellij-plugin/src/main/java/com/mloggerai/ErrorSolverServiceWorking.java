package com.mloggerai;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.messages.MessageBusConnection;
import com.mloggerai.plugin.MLoggerAISettings;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ErrorSolverServiceWorking {

    private static final OkHttpClient client = new OkHttpClient();
    private static final CloseableHttpClient httpClient = HttpClients.createDefault();
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    // ============================================================
    // ✅ Accesso lazy alle impostazioni del plugin (niente <clinit>)
    // ============================================================

    private static MLoggerAISettings getSettings() {
        return MLoggerAISettings.getInstance();
    }

    private static String getServerUrl() {
        return getSettings().getServerUrl();
    }

    private static String getModelName() {
        return getSettings().getModelName();
    }

    private static String getAccessToken() {
        return getSettings().getSystemServiceKey();
    }

    private static String getSystemPrompt() {
        return getSettings().getSystemPrompt();
    }

    private static String getOutputLanguage() {
        return getSettings().getOutputLanguage();
    }

    // ============================================================
    // Costruzione della richiesta JSON per l’AI
    // ============================================================

    private static JSONObject buildRequest(String text) {
        return new JSONObject()
                .put("model", getModelName())
                .put("temperature", 0.3)
                .put("max_tokens", 150)
                .put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "system")
                                .put("content", getSystemPrompt() + ". Rispondi sempre in lingua " + getOutputLanguage()))
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", text))
                );
    }

    // ============================================================
    // Gestione sincronizzata dei log (solo ultimo errore)
    // ============================================================

    public static void initSync(Project project, JBTextArea outputArea) {
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
                StringBuilder lastError = new StringBuilder();

                handler.addProcessListener(new ProcessAdapter() {
                    @Override
                    public void onTextAvailable(@NotNull ProcessEvent event, com.intellij.openapi.util.@NotNull Key outputType) {
                        String logLine = event.getText().trim();
                        if (!logLine.isEmpty() && outputType == ProcessOutputTypes.STDERR) {
                            // Mantiene solo l’ultimo messaggio d’errore
                            lastError.setLength(0);
                            lastError.append(logLine);
                        }
                    }

                    @Override
                    public void processTerminated(@NotNull ProcessEvent event) {
                        if (lastError.length() > 0) {
                            String solution = querySolverApache(lastError.toString());
                            if (!solution.isEmpty()) {
                                outputArea.append("⚡ Log: " + lastError + "\n");
                                outputArea.append("📘 AI: " + solution + "\n\n");
                                System.out.println("✅ Risposta AI:");
                                System.out.println(solution);
                                event.getProcessHandler().notifyTextAvailable(
                                        "📘 AI: " + solution + "\n", ProcessOutputTypes.STDOUT
                                );
                            }
                        }
                    }
                });
            }
        });
    }

    // ============================================================
    // Gestione sincronizzata dei log (solo ultimo errore) Async
    // ============================================================
    public static void initAsync(Project project, JBTextArea outputArea) {
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
                handler.addProcessListener(new ProcessAdapter() {
                    private final ExecutorService executor = Executors.newFixedThreadPool(2);

                    @Override
                    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull com.intellij.openapi.util.Key outputType) {
                        String logLine = event.getText().trim();
                        if (!logLine.isEmpty() && outputType == ProcessOutputTypes.STDERR) {

                            // ✅ esegui in background
                            executor.submit(() -> {
                                String solution = querySolverApache(logLine);
                                if (solution != null && !solution.isEmpty()) {
                                    SwingUtilities.invokeLater(() -> {
                                        outputArea.append("⚡ Log: " + logLine + "\n");
                                        outputArea.append("📘 AI: " + solution + "\n\n");
                                        event.getProcessHandler().notifyTextAvailable(
                                                "📘 AI: " + solution + "\n", ProcessOutputTypes.STDOUT
                                        );
                                    });
                                }
                            });
                        }
                    }

                    @Override
                    public void processTerminated(@NotNull ProcessEvent event) {
                        executor.shutdown();
                    }
                });
            }
        });
    }

    // ============================================================
    // Gestione sincronizzata dei log (solo ultimo errore) Async Non ripetuto
    // ============================================================
    public static void initNotRepetedAsync(Project project, JBTextArea outputArea) {
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
                StringBuilder lastError = new StringBuilder();

                handler.addProcessListener(new ProcessAdapter() {

                    @Override
                    public void onTextAvailable(@NotNull ProcessEvent event, com.intellij.openapi.util.@NotNull Key outputType) {
                        String logLine = event.getText().trim();
                        if (!logLine.isEmpty() && outputType == ProcessOutputTypes.STDERR) {
                            // memorizza solo l'ultimo messaggio d’errore
                            lastError.setLength(0);
                            lastError.append(logLine);
                        }
                    }

                    @Override
                    public void processTerminated(@NotNull ProcessEvent event) {
                        if (lastError.length() > 0) {
                            String log = lastError.toString();

                            // ✅ esegui in background
                            executor.submit(() -> {
                                String solution = querySolverApache(log);
                                if (!solution.isEmpty()) {
                                    SwingUtilities.invokeLater(() -> {
                                        outputArea.append("⚡ Log: " + log + "\n");
                                        outputArea.append("📘 AI: " + solution + "\n\n");
                                        event.getProcessHandler().notifyTextAvailable(
                                                "📘 AI: " + solution + "\n", ProcessOutputTypes.STDOUT
                                        );
                                    });
                                }
                            });
                        }
                    }
                });
            }
        });
    }


    // ============================================================
    // Variante bloccante (risponde in tempo reale a ogni errore)
    // ============================================================

    public static void initSyncBloccante(Project project, JBTextArea outputArea) {
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
                handler.addProcessListener(new ProcessAdapter() {
                    public void onTextAvailable(@NotNull ProcessEvent event, com.intellij.openapi.util.@NotNull Key outputType) {
                        String logLine = event.getText().trim();
                        if (!logLine.isEmpty() && outputType == ProcessOutputTypes.STDERR) {
                            String solution = querySolverApache(logLine);
                            if (!solution.isEmpty()) {
                                outputArea.append("⚡ Log: " + logLine + "\n");
                                outputArea.append("📘 AI: " + solution + "\n\n");
                                System.out.println("✅ Risposta AI:");
                                System.out.println(solution);
                                event.getProcessHandler().notifyTextAvailable(
                                        "📘 AI: " + solution + "\n", ProcessOutputTypes.STDOUT
                                );
                            }
                        }
                    }
                });
            }
        });
    }

    // ============================================================
    // HTTP client Apache per query sincrona
    // ============================================================

    private static String querySolverApache(String log) {
        try {
            HttpPost request = new HttpPost(getServerUrl() + "/chat/completions");
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Authorization", "Bearer " + getAccessToken());
            request.setEntity(new StringEntity(buildRequest(log).toString(), ContentType.APPLICATION_JSON));

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
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Errore di comunicazione con ErrorSolver: " + e.getMessage();
        }
    }

    // ============================================================
    // HTTP client OkHttp (fallback alternativo, non usato ora)
    // ============================================================

    private static String querySolver(String log) {
        try {
            JSONArray messagesArray = new JSONArray()
                    .put(new JSONObject().put("role", "system").put("content", getSystemPrompt()))
                    .put(new JSONObject().put("role", "user").put("content", log));

            JSONObject payload = new JSONObject()
                    .put("model", getModelName())
                    .put("messages", messagesArray)
                    .put("temperature", 0.3)
                    .put("max_tokens", 150)
                    .put("stream", false);

            RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json"));
            Request request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + getAccessToken())
                    .url(getServerUrl())
                    .post(body)
                    .build();

            okhttp3.Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                JSONObject jsonResponse = new JSONObject(response.body().string());
                JSONArray choices = jsonResponse.optJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                    return message.optString("content", null);
                }
            }
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return "Errore di comunicazione con ErrorSolver: " + e.getMessage();
        }
    }
}
