package com.mloggerai.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import okhttp3.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class MLoggerAISettingsConfigurable implements Configurable {

    public JBTextField serverUrlField;
    public JBTextField modelNameField;
    public JComboBox<String> modelNameDropdown;
    public JPanel modelPanel;
    public JTextArea systemPromptField;
    public JBPasswordField systemServiceKeyField;
    public JBTextField outputLanguageField;
    public JBTextField temperatureField;
    public JBTextField maxTokensField;
    public JPanel mainPanel;

    private boolean isLmStudioMode = false;
    private OkHttpClient httpClient;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return MLoggerAIBundle.message("settings.displayName");
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();

        serverUrlField = new JBTextField();
        modelNameField = new JBTextField();
        modelNameDropdown = new JComboBox<>();

        modelPanel = new JPanel(new BorderLayout());
        modelPanel.add(modelNameField, BorderLayout.CENTER);

        systemPromptField = new JTextArea(5, 40);
        systemPromptField.setLineWrap(true);
        systemPromptField.setWrapStyleWord(true);
        JBScrollPane systemPromptScroll = new JBScrollPane(systemPromptField);

        systemServiceKeyField = new JBPasswordField();
        outputLanguageField = new JBTextField();
        temperatureField = new JBTextField();
        maxTokensField = new JBTextField();

        systemServiceKeyField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(() -> checkServiceKeyAndSwitchInput());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(() -> checkServiceKeyAndSwitchInput());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(() -> checkServiceKeyAndSwitchInput());
            }
        });

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel(MLoggerAIBundle.message("settings.serverUrl")),
                        serverUrlField, 1, false)
                .addLabeledComponent(new JBLabel(MLoggerAIBundle.message("settings.modelName")),
                        modelPanel, 1, false)
                .addLabeledComponent(new JBLabel(MLoggerAIBundle.message("settings.systemPrompt")),
                        systemPromptScroll, 1, false)
                .addLabeledComponent(new JBLabel(MLoggerAIBundle.message("settings.serviceKey")),
                        systemServiceKeyField, 1, false)
                .addLabeledComponent(new JBLabel(MLoggerAIBundle.message("settings.outputLanguage")),
                        outputLanguageField, 1, false)
                .addLabeledComponent(new JBLabel(MLoggerAIBundle.message("settings.temperature")),
                        temperatureField, 1, false)
                .addLabeledComponent(new JBLabel(MLoggerAIBundle.message("settings.maxTokens")),
                        maxTokensField, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        mainPanel.setBorder(JBUI.Borders.empty(10));

        reset();

        // Forza il check dopo che tutto è inizializzato
        SwingUtilities.invokeLater(() -> checkServiceKeyAndSwitchInput());

        return mainPanel;
    }

    private void checkServiceKeyAndSwitchInput() {
        String key = String.valueOf(systemServiceKeyField.getPassword()).trim();
        boolean shouldUseLmStudio = "lm-studio".equalsIgnoreCase(key);

        if (shouldUseLmStudio && !isLmStudioMode) {
            // Passa al dropdown
            String currentValue = modelNameField.getText();
            modelPanel.removeAll();
            modelPanel.add(modelNameDropdown, BorderLayout.CENTER);
            modelPanel.revalidate();
            modelPanel.repaint();
            isLmStudioMode = true;

            fetchAndPopulateModelDropdown(currentValue);

        } else if (!shouldUseLmStudio && isLmStudioMode) {
            // Torna al campo testo
            String selectedValue = (String) modelNameDropdown.getSelectedItem();
            if (selectedValue != null && !selectedValue.startsWith("Errore") &&
                    !selectedValue.startsWith("Caricamento") && !selectedValue.startsWith("Nessun")) {
                modelNameField.setText(selectedValue);
            }
            modelPanel.removeAll();
            modelPanel.add(modelNameField, BorderLayout.CENTER);
            modelPanel.revalidate();
            modelPanel.repaint();
            isLmStudioMode = false;
        }
    }

    private void fetchAndPopulateModelDropdown(String valueToSelect) {
        SwingUtilities.invokeLater(() -> {
            modelNameDropdown.removeAllItems();
            modelNameDropdown.addItem("Caricamento modelli...");
        });

        new Thread(() -> {
            try {
                String urlString = serverUrlField.getText().trim();
                if (urlString.isEmpty()) {
                    urlString = "http://localhost:1234/v1";
                }

                if (urlString.endsWith("/")) {
                    urlString = urlString.substring(0, urlString.length() - 1);
                }

                urlString += "/models";

                Request request = new Request.Builder()
                        .url(urlString)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();

                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root = mapper.readTree(responseBody);
                        JsonNode dataArray = root.path("data");

                        SwingUtilities.invokeLater(() -> {
                            modelNameDropdown.removeAllItems();

                            if (dataArray.isArray() && dataArray.size() > 0) {
                                Iterator<JsonNode> it = dataArray.elements();
                                boolean hasModels = false;

                                while (it.hasNext()) {
                                    JsonNode modelNode = it.next();
                                    String id = modelNode.path("id").asText();
                                    if (!id.isEmpty()) {
                                        modelNameDropdown.addItem(id);
                                        hasModels = true;
                                    }
                                }

                                if (hasModels) {
                                    if (valueToSelect != null && !valueToSelect.isEmpty()) {
                                        modelNameDropdown.setSelectedItem(valueToSelect);
                                    }
                                } else {
                                    modelNameDropdown.addItem("Nessun modello trovato");
                                }
                            } else {
                                modelNameDropdown.addItem("Nessun modello disponibile");
                            }
                        });
                    } else {
                        int code = response.code();
                        SwingUtilities.invokeLater(() -> {
                            modelNameDropdown.removeAllItems();
                            modelNameDropdown.addItem("Errore HTTP: " + code);
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    modelNameDropdown.removeAllItems();
                    modelNameDropdown.addItem("Errore: " + e.getMessage());
                });
            }
        }).start();
    }

    @Override
    public boolean isModified() {
        MLoggerAISettings s = MLoggerAISettings.getInstance();

        boolean urlMod = !serverUrlField.getText().equals(s.getServerUrl());

        String currentModel = isLmStudioMode
                ? (String) modelNameDropdown.getSelectedItem()
                : modelNameField.getText();
        boolean modelMod = currentModel == null || !currentModel.equals(s.getModelName());

        boolean promptMod = !systemPromptField.getText().equals(s.getSystemPrompt());
        boolean keyMod = !String.valueOf(systemServiceKeyField.getPassword())
                .equals(s.getSystemServiceKey());
        boolean outLangMod = !outputLanguageField.getText().equals(s.getOutputLanguage());

        boolean tempMod = false;
        try {
            double t = Double.parseDouble(temperatureField.getText());
            tempMod = Math.abs(t - s.getTemperature()) > 0.0001;
        } catch (NumberFormatException ignored) {
            tempMod = true;
        }

        boolean tokensMod = false;
        try {
            int tk = Integer.parseInt(maxTokensField.getText());
            tokensMod = tk != s.getMaxTokensField();
        } catch (NumberFormatException ignored) {
            tokensMod = true;
        }

        return urlMod || modelMod || promptMod || keyMod || outLangMod || tempMod || tokensMod;
    }

    @Override
    public void apply() {
        MLoggerAISettings s = MLoggerAISettings.getInstance();

        s.setServerUrl(serverUrlField.getText());

        String modelToSave = isLmStudioMode
                ? (String) modelNameDropdown.getSelectedItem()
                : modelNameField.getText();
        if (modelToSave != null && !modelToSave.startsWith("Errore") &&
                !modelToSave.startsWith("Caricamento") && !modelToSave.startsWith("Nessun")) {
            s.setModelName(modelToSave);
        }

        s.setSystemPrompt(systemPromptField.getText());
        s.setSystemServiceKey(String.valueOf(systemServiceKeyField.getPassword()));
        s.setOutputLanguage(outputLanguageField.getText());

        try {
            double t = Double.parseDouble(temperatureField.getText());
            if (t >= 0.0 && t <= 2.0) s.setTemperature(t);
        } catch (NumberFormatException ignored) {}

        try {
            int tk = Integer.parseInt(maxTokensField.getText());
            if (tk > 0) s.setMaxTokens(tk);
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void reset() {
        MLoggerAISettings s = MLoggerAISettings.getInstance();

        serverUrlField.setText(s.getServerUrl());
        modelNameField.setText(s.getModelName());
        systemPromptField.setText(s.getSystemPrompt());
        systemServiceKeyField.setText(s.getSystemServiceKey());
        outputLanguageField.setText(s.getOutputLanguage());
        temperatureField.setText(String.valueOf(s.getTemperature()));
        maxTokensField.setText(String.valueOf(s.getMaxTokensField()));
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        serverUrlField = null;
        modelNameField = null;
        modelNameDropdown = null;
        modelPanel = null;
        systemPromptField = null;
        systemServiceKeyField = null;
        outputLanguageField = null;
        temperatureField = null;
        maxTokensField = null;
    }
}