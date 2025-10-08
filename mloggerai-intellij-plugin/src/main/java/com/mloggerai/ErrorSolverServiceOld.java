package com.mloggerai;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
//import com.intellij.openapi.util.Key;
//import com.intellij.execution.process.ProcessHandler;
//import com.intellij.execution.process.ProcessOutputTypes;


public class ErrorSolverServiceOld {

    private static final OkHttpClient client = new OkHttpClient();
    private static final CloseableHttpClient httpClient = HttpClients.createDefault();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    static String serverUrl = MLoggerAISettings.getInstance().getServerUrl();
    static String modelName = MLoggerAISettings.getInstance().getModelName();
    static String accessToken = MLoggerAISettings.getInstance().getSystemServiceKey();
    static String systemPrompt = MLoggerAISettings.getInstance().getSystemPrompt();
    static String outputLanguage = MLoggerAISettings.getInstance().getOutputLanguage();

    private static JSONObject buildRequest(String text) {
        return new JSONObject()
                .put("model", modelName)
                .put("temperature", 0.3)
                .put("max_tokens", 150)
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system")
                                .put("content", systemPrompt + ". Rispondi sempre in lingua " + outputLanguage))
                        .put(new JSONObject().put("role", "user").put("content", text))
                );
    }

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
                            // memorizza l'ultimo messaggio d’errore
                            lastError.setLength(0);
                            lastError.append(logLine);
                        }
                    }

                    @Override
                    public void processTerminated(@NotNull ProcessEvent event) {
                        if (lastError.length() > 0) {
                            String solution = querysolverApache(lastError.toString());
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
    public static void initSyncBloccante(Project project, JBTextArea outputArea) {
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
                handler.addProcessListener(new ProcessAdapter() {
                    public void onTextAvailable(@NotNull ProcessEvent event, com.intellij.openapi.util.@org.jetbrains.annotations.NotNull Key outputType) {
                        String logLine = event.getText().trim();
                        if (!logLine.isEmpty() & outputType == ProcessOutputTypes.STDERR) {
                            String solution = querysolverApache(logLine);
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

    private static String querysolverApache(String log) {
        try {

            HttpPost request = new HttpPost(serverUrl + "/chat/completions");
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Authorization", "Bearer " + accessToken);
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

    private static String querySolver(String log) {
        try {

            JSONArray messagesArray = new JSONArray();

            // System message
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messagesArray.put(systemMessage);

            // User message
            JSONObject userInput = new JSONObject();
            userInput.put("role", "user");
            userInput.put("content", log);  // <- pass the actual log
            messagesArray.put(userInput);

            JSONObject payload = new JSONObject();
            payload.put("model", modelName);
            payload.put("messages", messagesArray);
            payload.put("temperature", 0.3);
            payload.put("max_tokens", 150);
            payload.put("stream", false);

            RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json"));
            Request request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .url(serverUrl)
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
//    public static void initAsync(Project project, JBTextArea outputArea) {
//        MessageBusConnection connection = project.getMessageBus().connect();
//        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
//            @Override
//            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, ProcessHandler handler) {
//                handler.addProcessListener(new ProcessAdapter() {
//                    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
//                        String logLine = event.getText().trim();
//                        if (!logLine.isEmpty() & outputType == ProcessOutputTypes.STDERR) {
//                            // Call AI asynchronously
//                            querySolverAsync(logLine, solution -> {
//                                if (solution != null) {
//                                    // Append to outputArea on the EDT
//                                    javax.swing.SwingUtilities.invokeLater(() -> {
//                                        outputArea.append("⚡ Log: " + logLine + "\n");
//                                        outputArea.append("📘 AI: " + solution + "\n\n");
//                                        event.getProcessHandler().notifyTextAvailable(
//                                                "📘 AI: " + solution + "\n", ProcessOutputTypes.STDOUT
//                                        );
//                                    });
//                                }
//                            });
//                        }
//                    }
//                });
//            }
//        });
//    }
//
//    private static void querySolverAsync(String log, java.util.function.Consumer<String> callback) {
//        try {
//            String serverUrl = MLoggerAISettings.getInstance().getServerUrl();
//            String modelName = MLoggerAISettings.getInstance().getModelName();
//            String accessToken = MLoggerAISettings.getInstance().getSystemServiceKey();
//            String systemPrompt = MLoggerAISettings.getInstance().getSystemPrompt();
//
//            JSONArray messagesArray = new JSONArray();
//
//            JSONObject systemMessage = new JSONObject();
//            systemMessage.put("role", "system");
//            systemMessage.put("content", systemPrompt);
//            messagesArray.put(systemMessage);
//
//            JSONObject userInput = new JSONObject();
//            userInput.put("role", "user");
//            userInput.put("content", log);
//            messagesArray.put(userInput);
//
//            JSONObject payload = new JSONObject();
//            payload.put("model", modelName);
//            payload.put("messages", messagesArray);
//            payload.put("temperature", 0.3);
//            payload.put("max_tokens", 150);
//            payload.put("stream", false);
//
//            RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json"));
//            Request request = new Request.Builder()
//                    .addHeader("Authorization", "Bearer " + accessToken)
//                    .url(serverUrl)
//                    .post(body)
//                    .build();
//
//            client.newCall(request).enqueue(new okhttp3.Callback() {
//                @Override
//                public void onFailure(okhttp3.@NotNull Call call, java.io.@NotNull IOException e) {
//                    callback.accept("Errore di comunicazione: " + e.getMessage());
//                }
//
//                @Override
//                public void onResponse(okhttp3.@NotNull Call call, okhttp3.@NotNull Response response) throws java.io.IOException {
//                    if (response.isSuccessful() && response.body() != null) {
//                        JSONObject jsonResponse = new JSONObject(response.body().string());
//                        JSONArray choices = jsonResponse.optJSONArray("choices");
//                        if (choices != null && !choices.isEmpty()) {
//                            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
//                            callback.accept(message.optString("content", null));
//                            return;
//                        }
//                    }
//                    callback.accept(null);
//                }
//            });
//
//        } catch (Exception e) {
//            callback.accept("Errore: " + e.getMessage());
//        }
//    }


}
