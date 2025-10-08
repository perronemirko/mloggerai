package com.mloggerai.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.IOException;

public class MLoggerAISuggestAction extends AnAction {

    private static final Logger log = LoggerFactory.getLogger(MLoggerAISuggestAction.class);
    private final OkHttpClient client = new OkHttpClient();

    public MLoggerAISuggestAction() {
        super();
    }

    public String extractResponseFromJson(String json) {
        Gson gson = new Gson();
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        log.info("JsonObject -> {}", json);
        if (obj.has("choices")) {
            JsonArray choices = obj.getAsJsonArray("choices");
            if (!choices.isEmpty()) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                if (firstChoice.has("text")) {
                    return firstChoice.get("text").getAsString();
                }
            }
        }

        return "";
    }

    private String indentResponse(Editor editor, String response) {
        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        int lineNumber = document.getLineNumber(offset);
        int lineStartOffset = document.getLineStartOffset(lineNumber);
        String lineText = document.getText(new TextRange(lineStartOffset, offset));

        // Calcolo l'indentazione attuale come spazi/tab all'inizio della linea fino al caret
        StringBuilder indentBuilder = new StringBuilder();
        for (char c : lineText.toCharArray()) {
            if (c == ' ' || c == '\t') {
                indentBuilder.append(c);
            } else {
                break;
            }
        }
        String indent = indentBuilder.toString();

        // Ora applico questa indentazione a ogni riga della risposta
        String[] lines = response.split("\n");
        StringBuilder indentedResponse = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) indentedResponse.append("\n");
            indentedResponse.append(indent).append(lines[i]);
        }

        return indentedResponse.toString();
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        Project project = e.getProject();

        if (editor == null || project == null) {
            Messages.showErrorDialog("Nessun editor aperto.", "Errore");
            return;
        }

        String serverUrl = MLoggerAISettings.getInstance().getServerUrl();
        String modelName = MLoggerAISettings.getInstance().getModelName();
        String accessToken = MLoggerAISettings.getInstance().getSystemServiceKey();
        String systemPrompt = MLoggerAISettings.getInstance().getSystemPrompt();
        Document document = editor.getDocument();

        String terminalError = "";
        String payload;
        try {
            JSONObject json = new JSONObject();

            JSONArray messagesArray = new JSONArray();

            // Add system message
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messagesArray.put(systemMessage);

            // Add user message
            JSONObject userInput = new JSONObject();
            userInput.put("role", "user");
            userInput.put("content", terminalError);
            messagesArray.put(userInput);

            json.put("model", modelName);
            json.put("messages", messagesArray);
            json.put("temperature", 0.3);
            json.put("max_tokens", 150);
            json.put("stream", false);

            payload = json.toString();

        } catch (Exception ex) {
            Messages.showErrorDialog(project, "Errore nel creare la richiesta JSON: " + ex.getMessage(), "Errore");
            return;
        }

        RequestBody body = RequestBody.create(payload, MediaType.get("application/json"));

        Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url(serverUrl)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException ex) {
                SwingUtilities.invokeLater(() ->
                        Messages.showErrorDialog(project, "Errore richiesta: " + ex.getMessage(), "Errore")
                );
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    SwingUtilities.invokeLater(() ->
                            Messages.showErrorDialog(project, "Risposta non valida: " + response.message(), "Errore")
                    );
                    return;
                }

                assert response.body() != null;

                String jsonResponse = response.body().string();
                SwingUtilities.invokeLater(() -> {
                    String codeToInsert = extractResponseFromJson(jsonResponse);

                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        Document document = editor.getDocument();
                        int offset = editor.getCaretModel().getOffset();
                        String indentedCode = indentResponse(editor, codeToInsert); // se vuoi indentare
                        ApplicationManager.getApplication().invokeLater(() -> {
                            WriteCommandAction.runWriteCommandAction(project, () -> {
                                document.insertString(offset, indentedCode);
                            });
                        });
                        editor.getCaretModel().moveToOffset(offset + indentedCode.length());
                    });
                });
            }
        });
    }
}
