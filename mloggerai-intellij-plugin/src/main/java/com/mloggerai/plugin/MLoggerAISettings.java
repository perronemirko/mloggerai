package com.mloggerai.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "MLoggerAISettings",
        storages = {@Storage("MLoggerAISettings.xml")}
)
@Service(Service.Level.APP)
public final class MLoggerAISettings implements PersistentStateComponent<MLoggerAISettings.State> {

    public static class State {
        public String serverUrl = "http://localhost:1234/v1";
        public String modelName = "lmstudio-community/llama-3.2-3b-instruct";
        public String systemPrompt = "Trova il bug e rispondi in modo conciso fornendo anche un solo esempio di codice";
        public String systemServiceKey = "lm-studio";
        public String outputLanguage = "italiano";
        public double temperature = 0.3;
        public int maxTokens = 200;
    }

    private State myState = new State();

    public static MLoggerAISettings getInstance() {
        return ApplicationManager.getApplication().getService(MLoggerAISettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public String getServerUrl() {
        return myState.serverUrl;
    }

    public void setServerUrl(String url) {
        myState.serverUrl = url;
    }

    public String getModelName() {
        return myState.modelName;
    }

    public String getSystemServiceKey() {
        return myState.systemServiceKey;
    }

    public String getOutputLanguage() {
        return myState.outputLanguage;
    }

    public double getTemperature() {
        return myState.temperature;
    }

    public int getMaxTokensField() {
        return myState.maxTokens;
    }

    public void setModelName(String modelName) {
        this.myState.modelName = modelName;
    }

    public String getSystemPrompt() {
        return myState.systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.myState.systemPrompt = systemPrompt;
    }

    public void setSystemServiceKey(String systemServiceKey) {
        this.myState.systemServiceKey = systemServiceKey;
    }

    public void setOutputLanguage(String outputLanguage) {
        this.myState.outputLanguage = outputLanguage;
    }

    public void setTemperature(double temperature) {
        this.myState.temperature = temperature;
    }

    public void setMaxTokens(int maxTokens) {
        this.myState.maxTokens = maxTokens;
    }

}
