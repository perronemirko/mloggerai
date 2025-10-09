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
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ErrorSolverService {

    private static final OkHttpClient client = new OkHttpClient();
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
    private static volatile boolean isInitialized = false;
    private static volatile MLoggerAISettings cachedSettings = null;

    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "(?i)(\\b(" +
                    "error|" +
                    "exception|" +
                    "failed|" +
                    "traceback|" +
                    "fatal|" +
                    "panic|" +
                    "segmentation fault|" +
                    "undefined|" +
                    "not found|" +
                    "denied|" +
                    "unreachable|" +
                    "nullpointer|" +
                    "stacktrace|" +
                    "abort|" +
                    "crash|" +
                    "timeout|" +
                    "unexpected|" +
                    "another exception occurred|" +
                    "the above exception was the direct cause|" +
                    "during handling of the above exception|" +
                    "raise|" +
                    "in <module>" +
                    "at " +
                    ")\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WARNING_PATTERN = Pattern.compile(
            "(?i)(\\b(" +
                    "warn|" +
                    "warning|" +
                    "deprecated|" +
                    "slow|" +
                    "unstable|" +
                    "not recommended|" +
                    "will be removed" +
                    ")\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static MLoggerAISettings getSettings(Project project) {
        MLoggerAISettings settings = MLoggerAISettings.getInstance();
        if (settings == null ||
                settings.getServerUrl() == null ||
                settings.getSystemServiceKey() == null ||
                settings.getModelName() == null) {
            throw new IllegalStateException("Plugin non inizializzato correttamente. Riprova dopo qualche secondo.");
        }
        return settings;
    }

    private static JSONObject buildRequest(MLoggerAISettings settings, String text) {
        return new JSONObject()
                .put("model", settings.getModelName())
                .put("temperature", settings.getTemperature())
                .put("max_tokens", settings.getMaxTokensField())
                .put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "system")
                                .put("content", settings.getSystemPrompt() + ". Rispondi sempre in lingua " + settings.getOutputLanguage()))
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", text))
                );
    }

    public static void init(Project project, JBTextArea outputArea, JCheckBox activateDebug, JCheckBox verbose) {
        if (isInitialized) {
            if (activateDebug.isSelected()) {
                SwingUtilities.invokeLater(() ->
                        outputArea.append("⚠️ [DEBUG] ErrorSolver già inizializzato, skip\n"));
            }
            return;
        }

        try {
            registerExecutionListener(project, outputArea, activateDebug, verbose);
            isInitialized = true;
            if (activateDebug.isSelected()) {
                SwingUtilities.invokeLater(() -> {
                    outputArea.append("🔧 [DEBUG] Avvio inizializzazione ErrorSolver...\n");
                    outputArea.append("✅ [DEBUG] Listener registrato SUBITO!\n");
                    outputArea.append("⏰ [DEBUG] Timestamp: " + System.currentTimeMillis() + "\n");
                });
            }
        } catch (Exception e) {
            if (activateDebug.isSelected()) {
                SwingUtilities.invokeLater(() ->
                        outputArea.append("❌ [DEBUG] Errore registrazione listener: " + e.getMessage() + "\n"));
            }
        }

        executor.submit(() -> {
            if (activateDebug.isSelected()) {
                SwingUtilities.invokeLater(() ->
                        outputArea.append("🔄 [DEBUG] Pre-caricamento settings in background...\n")
                );
            }
            for (int i = 0; i < 10 && cachedSettings == null; i++) {
                try {
                    cachedSettings = getSettings(project);
                    final int attempt = i + 1;
                    SwingUtilities.invokeLater(() ->
                            outputArea.append("✅ [DEBUG] Settings PRE-CARICATE al tentativo " + attempt + "!\n\n"));
                    break;
                } catch (IllegalStateException e) {
                    if (i < 9) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            if (cachedSettings == null) {
                if (activateDebug.isSelected()) {
                    SwingUtilities.invokeLater(() ->
                            outputArea.append("⚠️ [DEBUG] Impossibile pre-caricare settings, verranno caricate al bisogno\n\n"));
                }
            }
        });
    }

    public static void initGpt(Project project, JBTextArea outputArea,
                               JCheckBox activateDebug, JCheckBox verbose) {
        if (isInitialized) {
            if (activateDebug.isSelected()) {
                SwingUtilities.invokeLater(() ->
                        outputArea.append("⚠️ [DEBUG] ErrorSolver già inizializzato, skip\n"));
            }
            return;
        }

        try {
            registerExecutionListener(project, outputArea, activateDebug, verbose);
            isInitialized = true;

            if (activateDebug.isSelected()) {
                SwingUtilities.invokeLater(() -> {
                    outputArea.append("🔧 [DEBUG] Avvio inizializzazione ErrorSolver...\n");
                    outputArea.append("✅ [DEBUG] Listener registrato SUBITO!\n");
                    outputArea.append("⏰ [DEBUG] Timestamp: " + System.currentTimeMillis() + "\n");
                });
            }

        } catch (Exception e) {
            if (activateDebug.isSelected()) {
                SwingUtilities.invokeLater(() ->
                        outputArea.append("❌ [DEBUG] Errore registrazione listener: " + e.getMessage() + "\n"));
            }
        }

        executor.submit(() -> {
            if (activateDebug.isSelected()) {
                SwingUtilities.invokeLater(() ->
                        outputArea.append("🔄 [DEBUG] Pre-caricamento settings in background...\n")
                );
            }
            for (int i = 0; i < 10 && cachedSettings == null; i++) {
                try {
                    cachedSettings = getSettings(project);
//                    final int attempt = i + 1;
//                    SwingUtilities.invokeLater(() ->
//                            outputArea.append("✅ [DEBUG] Settings PRE-CARICATE al tentativo " + attempt + "!\n\n"));
                    break;
                } catch (IllegalStateException e) {
                    if (i < 9) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            if (cachedSettings == null) {
                if (activateDebug.isSelected()) {
                    SwingUtilities.invokeLater(() ->
                            outputArea.append("⚠️ [DEBUG] Impossibile pre-caricare settings, verranno caricate al bisogno\n\n"));
                }
            }
        });
    }


    private static void callRestAI(
            Project project,
            JBTextArea outputArea,
            JCheckBox activateDebug,
            StringBuilder lastError,
            int exitCode,
            @NotNull ProcessEvent event) {
        String log = !lastError.isEmpty() ? lastError.toString() : "Process exited with code " + exitCode;
        if (activateDebug.isSelected()) {
            SwingUtilities.invokeLater(() ->
                    outputArea.append("📡 [DEBUG] Preparazione chiamata AI...\n"));
        }

        executor.submit(() -> {
            MLoggerAISettings settings = cachedSettings;

            if (settings == null) {
                if (activateDebug.isSelected()) {
                    SwingUtilities.invokeLater(() ->
                            outputArea.append("🔄 [DEBUG] Cache vuota, caricamento settings...\n"));
                }
                int retries = 5;
                for (int i = 0; i < retries && settings == null; i++) {
                    try {
                        settings = getSettings(project);
                        cachedSettings = settings;
                        final int attempt = i + 1;
                        SwingUtilities.invokeLater(() ->
                                outputArea.append("✅ [DEBUG] Settings recuperate al tentativo " + attempt + "!\n"));
                    } catch (IllegalStateException e) {
                        final int remainingRetries = retries - i - 1;
                        SwingUtilities.invokeLater(() ->
                                outputArea.append("⚠️ [DEBUG] Retry rimanenti: " + remainingRetries + "\n"));

                        if (remainingRetries > 0) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
            } else {
                if (activateDebug.isSelected()) {
                    SwingUtilities.invokeLater(() ->
                            outputArea.append("✅ [DEBUG] Uso settings dalla CACHE!\n"));
                }
            }

            if (settings == null) {
                if (activateDebug.isSelected()) {
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append("❌ [DEBUG] Settings non disponibili\n");
                        outputArea.append("💡 [DEBUG] Riavvia l'IDE o verifica le impostazioni\n\n");
                    });
                }
                return;
            }

            if (activateDebug.isSelected()) {
                SwingUtilities.invokeLater(() ->
                        outputArea.append("📤 [DEBUG] Invio richiesta API...\n"));
            }

            MLoggerAISettings finalSettings = settings;
            querySolverAsync(finalSettings, log, solution -> {
                if (solution != null) {
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append("\n═══════════════════════════════════════\n");
                        outputArea.append("⚡ Log: " + log + "\n");
                        outputArea.append("✨🤖 AI: " + solution + "\n");
                        outputArea.append("═══════════════════════════════════════\n\n");
                        event.getProcessHandler().notifyTextAvailable(
                                "\n" + "═══════════════════════════════════════" + "\n" +
                                        "✨🧠 AI: " + solution + "\n" +
                                        "═══════════════════════════════════════\n\n", ProcessOutputTypes.STDOUT
                        );
                    });
                } else {
                    SwingUtilities.invokeLater(() ->
                            outputArea.append("⚠️ [MISSING] Risposta AI vuota\n"));
                }
            });
        });
    }

    private static void registerExecutionListener(Project project, JBTextArea outputArea, JCheckBox activateDebug, JCheckBox verbose) {

        if (activateDebug.isSelected()) {
            SwingUtilities.invokeLater(() ->
                    outputArea.append("📡 [DEBUG] Inizio registrazione listener...\n"));
        }

        MessageBusConnection projectConnection = project.getMessageBus().connect();
        if (activateDebug.isSelected()) {
            SwingUtilities.invokeLater(() ->
                    outputArea.append("🔌 [DEBUG] MessageBus connesso\n"));
        }

        ExecutionListener listener = new ExecutionListener() {
            @Override
            public void processStartScheduled(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
                if (activateDebug.isSelected()) {
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append("📅 [DEBUG] *** PROCESSO SCHEDULATO ***: " + executorId + " | " + env.getRunProfile().getName() + "\n");
                    });
                }
            }

            @Override
            public void processStarting(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
                if (activateDebug.isSelected()) {
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append("⏳ [DEBUG] *** PROCESSO IN AVVIO ***: " + executorId + "\n");
                    });
                }
            }

            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
                if (!verbose.isSelected()) {
                    outputArea.setText("");
                }
                if (activateDebug.isSelected()) {
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append("🚀 [DEBUG] *** PROCESSO AVVIATO ***: " + executorId + " | " + env.getRunProfile().getName() + "\n");
                        outputArea.append("🚀 [DEBUG] Handler class: " + handler.getClass().getName() + "\n");
                        outputArea.append("🚀 [DEBUG] Is terminated: " + handler.isProcessTerminated() + "\n");
                        outputArea.append("🚀 [DEBUG] Is terminating: " + handler.isProcessTerminating() + "\n");
                    });
                }

                // Classe helper per mantenere il tipo di output
                class LogEntry {
                    final String line;
                    final com.intellij.openapi.util.Key type;

                    LogEntry(String line, com.intellij.openapi.util.Key type) {
                        this.line = line;
                        this.type = type;
                    }
                }

                final List<LogEntry> initialBuffer = new ArrayList<>();
                final boolean[] bufferComplete = {false};
                final int MIN_LINES_BEFORE_ANALYSIS = 150;
                final StringBuilder lastError = new StringBuilder();
                final StringBuilder allOutput = new StringBuilder();
                final boolean[] hasReceivedOutput = {false};

                // Aggiungi listener PRIMA di fare qualsiasi altra cosa
                ProcessAdapter adapter = new ProcessAdapter() {

                    @Override
                    public void startNotified(@NotNull ProcessEvent event) {
                        if (activateDebug.isSelected()) {
                            SwingUtilities.invokeLater(() -> {
                                outputArea.append("🎬 [DEBUG] startNotified - processo realmente iniziato\n");
                            });
                        }
                    }

                    @Override
                    public void onTextAvailable(@NotNull ProcessEvent event, com.intellij.openapi.util.@NotNull Key outputType) {
                        hasReceivedOutput[0] = true;
                        String logLine = event.getText();

                        final int rawLength = logLine.length();
                        final String typeStr = outputType.toString();
                        logLine = logLine.trim();
                        final String lambdaLogLine = logLine;

                        if (activateDebug.isSelected()) {
                            SwingUtilities.invokeLater(() -> {
                                outputArea.append("📥 [DEBUG] RAW onTextAvailable | Length: " + rawLength + " | Type: " + typeStr + "\n");
                                outputArea.append("📥 [DEBUG] Content: " + lambdaLogLine + "\n");
                            });
                        }

                        if (logLine.isEmpty()) {
                            return;
                        }
                        final String log = logLine;
                        // Mostra output in verbose mode
                        if (verbose.isSelected() && !activateDebug.isSelected()) {
                            SwingUtilities.invokeLater(() -> {
                                outputArea.append(log + "\n");
                            });
                        }

                        // FASE 1: Accumula le prime 20 righe
                        if (!bufferComplete[0]) {
                            initialBuffer.add(new LogEntry(logLine, outputType));
                            allOutput.append(logLine);
                            if (activateDebug.isSelected()) {
                                final int currentSize = initialBuffer.size();
                                SwingUtilities.invokeLater(() -> {
                                    outputArea.append("🔵 [BUFFER] Accumulando riga " + currentSize + "/" + MIN_LINES_BEFORE_ANALYSIS + "\n");
                                });
                            }

                            if (initialBuffer.size() >= MIN_LINES_BEFORE_ANALYSIS) {
                                flushBuffer(initialBuffer, bufferComplete, lastError, activateDebug, outputArea, allOutput);
                            }
                            return;
                        }

                        // FASE 2: Dopo il buffer, processa normalmente

                        processLogLine(logLine, outputType, lastError, activateDebug, outputArea);
                    }

                    @Override
                    public void processTerminated(@NotNull ProcessEvent event) {

                        int exitCode = event.getExitCode();
                        if (activateDebug.isSelected()) {
                            SwingUtilities.invokeLater(() -> {
                                outputArea.append("⏹️ [DEBUG] *** PROCESSO TERMINATO *** | Exit code: " + exitCode + "\n");
                                outputArea.append("⏹️ [DEBUG] Ha ricevuto output: " + hasReceivedOutput[0] + "\n");
                                outputArea.append("⏹️ [DEBUG] Output totale: " + allOutput.length() + " chars\n");
                                outputArea.append("⏹️ [DEBUG] Errori catturati: " + (!lastError.isEmpty() ? "SÌ (" + lastError.length() + " chars)" : "NO") + "\n");
                            });
                        }

                        boolean b = exitCode == 0 && ERROR_PATTERN.matcher(allOutput).find();
                        if (!lastError.isEmpty() || exitCode != 0) {

                            callRestAI(project, outputArea, activateDebug, allOutput.append(Objects.requireNonNull(event.getProcessHandler().getProcessInput())), exitCode, event);

                        } else if (b) {
                            callRestAI(project, outputArea, activateDebug, allOutput.append(Objects.requireNonNull(event.getProcessHandler().getProcessInput())), exitCode, event);
                        } else {
                            if (activateDebug.isSelected()) {
                                SwingUtilities.invokeLater(() ->
                                        outputArea.append("ℹ️ [DEBUG] Nessun errore da analizzare\n\n"));
                            }
                            callRestAI(project, outputArea, activateDebug, allOutput.append(Objects.requireNonNull(event.getProcessHandler().getProcessInput())), exitCode, event);
                        }
                        // Se il buffer non è completo, svuotalo
                        if (!bufferComplete[0] && !initialBuffer.isEmpty()) {
                            if (activateDebug.isSelected()) {
                                final int bufSize = initialBuffer.size();
                                SwingUtilities.invokeLater(() -> {
                                    outputArea.append("⚠️ [BUFFER] Processo terminato con " + bufSize + " righe, analisi forzata\n");
                                });
                            }
                            flushBuffer(initialBuffer, bufferComplete, lastError, activateDebug, outputArea, allOutput);
                        }
                    }
                };

                handler.addProcessListener(adapter);

                if (activateDebug.isSelected()) {
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append("✅ [DEBUG] ProcessListener aggiunto al handler\n");
                        if (handler.isProcessTerminated()) {
                            outputArea.append("⚠️ [DEBUG] ATTENZIONE: Il processo è già terminato quando aggiungiamo il listener!\n");
                            outputArea.append("⚠️ [DEBUG] Tutto l'output è stato perso prima della registrazione.\n");
                        }
                    });
                }

                // Se il processo è già terminato, forza la chiamata a processTerminated
                if (handler.isProcessTerminated()) {
                    if (activateDebug.isSelected()) {
                        SwingUtilities.invokeLater(() -> {
                            outputArea.append("🔄 [DEBUG] Tentativo di recupero output perso...\n");
                        });
                    }
                }
            }
        };

        projectConnection.subscribe(ExecutionManager.EXECUTION_TOPIC, listener);
        if (activateDebug.isSelected()) {
            SwingUtilities.invokeLater(() ->
                    outputArea.append("✅ [DEBUG] Listener registrato!\n\n"));
        }
    }

    private static void flushBuffer(List<?> buffer, boolean[] bufferComplete,
                                    StringBuilder lastError, JCheckBox activateDebug,
                                    JBTextArea outputArea, StringBuilder allOutput) {
        bufferComplete[0] = true;

        if (activateDebug.isSelected()) {
            final int bufSize = buffer.size();
            SwingUtilities.invokeLater(() -> {
                outputArea.append("✅ [BUFFER] Svuotamento buffer (" + bufSize + " righe)...\n");
            });
        }

        for (Object obj : buffer) {
            try {
                String line = (String) obj.getClass().getField("line").get(obj);
                com.intellij.openapi.util.Key type = (com.intellij.openapi.util.Key) obj.getClass().getField("type").get(obj);

                allOutput.append(line).append("\n");
                processLogLine(line, type, lastError, activateDebug, outputArea);
            } catch (Exception e) {
                // Ignore
            }
        }
        buffer.clear();
    }

    private static void processLogLine(String logLine, com.intellij.openapi.util.Key outputType,
                                       StringBuilder lastError, JCheckBox activateDebug,
                                       JBTextArea outputArea) {
        final String type;
        final boolean isError;
        final boolean isWarning;
        boolean matchedErrorPattern = ERROR_PATTERN.matcher(logLine).find() || logLine.contains("at ");
        boolean matchedWarningPattern = WARNING_PATTERN.matcher(logLine).find();

        if (outputType == ProcessOutputTypes.STDERR) {
            type = "STDERR";
            isError = true;
            isWarning = false;
        } else if (outputType == ProcessOutputTypes.STDOUT) {
            type = "STDOUT";
            isError = matchedErrorPattern;
            isWarning = matchedWarningPattern && !isError;
        } else if (outputType == ProcessOutputTypes.SYSTEM) {
            type = "SYSTEM";
            isError = matchedErrorPattern;
            isWarning = matchedWarningPattern && !isError;
        } else {
            type = "UNKNOWN";
            isError = false;
            isWarning = false;
        }

        final String displayLine = logLine.substring(0, Math.min(80, logLine.length()));
        if (activateDebug.isSelected()) {
            SwingUtilities.invokeLater(() -> {
                String prefix = isError ? "🔴" : (isWarning ? "🟡" : "🟢");
                outputArea.append(prefix + " [DEBUG] " + type + ": " + displayLine + "...\n");
            });
        }

        if (isError) {
            lastError.append(logLine).append("\n");
        }
    }

    private static void querySolverAsync(MLoggerAISettings settings, String
            log, java.util.function.Consumer<String> callback) {
        try {
            JSONObject payload = buildRequest(settings, log);
            RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json"));

            Request request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + settings.getSystemServiceKey())
                    .addHeader("Content-Type", "application/json")
                    .url(settings.getServerUrl() + "/chat/completions")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    callback.accept("❌ Errore di comunicazione: " + e.getMessage());
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.accept("❌ Errore API: status " + response.code());
                        return;
                    }

                    String body = response.body().string();
                    try {
                        JSONObject json = new JSONObject(body);
                        JSONArray choices = json.optJSONArray("choices");
                        if (choices != null && !choices.isEmpty()) {
                            String content = choices.getJSONObject(0)
                                    .getJSONObject("message")
                                    .optString("content", "")
                                    .trim();
                            callback.accept(content);
                        } else {
                            callback.accept("⚠️ Nessuna risposta dall'AI.");
                        }
                    } catch (Exception e) {
                        callback.accept("❌ Errore parsing risposta AI: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            callback.accept("❌ Errore nella richiesta: " + e.getMessage());
        }
    }

    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
