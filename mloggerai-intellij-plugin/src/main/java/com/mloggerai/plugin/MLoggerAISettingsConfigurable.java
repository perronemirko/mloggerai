package com.mloggerai.plugin;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MLoggerAISettingsConfigurable implements Configurable {

    private JBTextField serverUrlField;
    private JBTextField modelNameField;
    private JTextArea systemPromptField;
    private JBPasswordField systemServiceKeyField;
    private JBTextField outputLanguageField;
    private JBTextField temperatureField;
    private JBTextField maxTokensField;
    private JPanel mainPanel;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return MLoggerAIBundle.message("settings.displayName");
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        serverUrlField = new JBTextField();
        modelNameField = new JBTextField();

        systemPromptField = new JTextArea(5, 40);
        systemPromptField.setLineWrap(true);
        systemPromptField.setWrapStyleWord(true);
        JScrollPane systemPromptScroll = new JScrollPane(systemPromptField);

        systemServiceKeyField = new JBPasswordField();
        outputLanguageField = new JBTextField();
        temperatureField = new JBTextField();
        maxTokensField = new JBTextField();

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(
                        new JBLabel(MLoggerAIBundle.message("settings.serverUrl")),
                        serverUrlField,
                        1,
                        false)
                .addLabeledComponent(
                        new JBLabel(MLoggerAIBundle.message("settings.modelName")),
                        modelNameField,
                        1,
                        false)
                .addLabeledComponent(
                        new JBLabel(MLoggerAIBundle.message("settings.systemPrompt")),
                        systemPromptScroll,
                        1,
                        false)
                .addLabeledComponent(
                        new JBLabel(MLoggerAIBundle.message("settings.serviceKey")),
                        systemServiceKeyField,
                        1,
                        false)
                .addLabeledComponent(
                        new JBLabel(MLoggerAIBundle.message("settings.outputLanguage")),
                        outputLanguageField,
                        1,
                        false)
                .addLabeledComponent(
                        new JBLabel(MLoggerAIBundle.message("settings.temperature")),
                        temperatureField,
                        1,
                        false)
                .addLabeledComponent(
                        new JBLabel(MLoggerAIBundle.message("settings.maxTokens")),
                        maxTokensField,
                        1,
                        false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        mainPanel.setBorder(JBUI.Borders.empty(10));

        reset();
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        MLoggerAISettings settings = MLoggerAISettings.getInstance();

        boolean urlModified = !serverUrlField.getText().equals(settings.getServerUrl());
        boolean modelModified = !modelNameField.getText().equals(settings.getModelName());
        boolean systemPromptModified = !systemPromptField.getText().equals(settings.getSystemPrompt());
        boolean systemServiceKeyModified = !String.valueOf(systemServiceKeyField.getPassword()).equals(settings.getSystemServiceKey());
        boolean outputLanguageModified = !outputLanguageField.getText().equals(settings.getOutputLanguage());

        boolean temperatureModified = false;
        try {
            double fieldTemp = Double.parseDouble(temperatureField.getText());
            temperatureModified = Math.abs(fieldTemp - settings.getTemperature()) > 0.0001;
        } catch (NumberFormatException e) {
            temperatureModified = true;
        }

        boolean maxTokensModified = false;
        try {
            int fieldTokens = Integer.parseInt(maxTokensField.getText());
            maxTokensModified = fieldTokens != settings.getMaxTokensField();
        } catch (NumberFormatException e) {
            maxTokensModified = true;
        }

        return urlModified ||
                modelModified ||
                systemPromptModified ||
                systemServiceKeyModified ||
                outputLanguageModified ||
                temperatureModified ||
                maxTokensModified;
    }

    @Override
    public void apply() {
        MLoggerAISettings settings = MLoggerAISettings.getInstance();
        settings.setServerUrl(serverUrlField.getText());
        settings.setModelName(modelNameField.getText());
        settings.setSystemPrompt(systemPromptField.getText());
        settings.setSystemServiceKey(String.valueOf(systemServiceKeyField.getPassword()));
        settings.setOutputLanguage(outputLanguageField.getText());

        try {
            double temp = Double.parseDouble(temperatureField.getText());
            if (temp >= 0.0 && temp <= 2.0) {
                settings.setTemperature(temp);
            }
        } catch (NumberFormatException e) {
            // Keep previous value on error
        }

        try {
            int tokens = Integer.parseInt(maxTokensField.getText());
            if (tokens > 0) {
                settings.setMaxTokens(tokens);
            }
        } catch (NumberFormatException e) {
            // Keep previous value on error
        }
    }

    @Override
    public void reset() {
        MLoggerAISettings settings = MLoggerAISettings.getInstance();
        serverUrlField.setText(settings.getServerUrl());
        modelNameField.setText(settings.getModelName());
        systemPromptField.setText(settings.getSystemPrompt());
        systemServiceKeyField.setText(settings.getSystemServiceKey());
        outputLanguageField.setText(settings.getOutputLanguage());
        temperatureField.setText(String.valueOf(settings.getTemperature()));
        maxTokensField.setText(String.valueOf(settings.getMaxTokensField()));
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        serverUrlField = null;
        modelNameField = null;
        systemPromptField = null;
        systemServiceKeyField = null;
        outputLanguageField = null;
        temperatureField = null;
        maxTokensField = null;
    }
}