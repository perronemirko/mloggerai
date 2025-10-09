package com.mloggerai.plugin

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextArea
import com.intellij.util.messages.MessageBusConnection
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

object ErrorSolverService {
    private lateinit var connection: MessageBusConnection
    private val client = OkHttpClient()
    private val executor = Executors.newSingleThreadExecutor() // Executor for asynchronous tasks

    fun init(project: Project, outputArea: JBTextArea) {
        connection = project.messageBus.connect()
        connection.subscribe(
            com.intellij.execution.ExecutionManager.EXECUTION_TOPIC,
            object : ExecutionListener {
                override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
                    handler.addProcessListener(object : ProcessListener {
                        override fun processTerminated(event: ProcessEvent) {
                            // Handle termination if necessary
                        }

                        override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
                            // Handle pre-termination if necessary
                        }

                        override fun onTextAvailable(event: ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
                            val logLine = event.text.trim()
                            if (logLine.isNotEmpty()) {
                                executor.submit {
                                    val solution = querySolver(logLine)
                                    if (solution != null) {
                                        // Ensure this runs on the UI thread
                                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                            outputArea.append("⚡ Log: $logLine\n")
                                            outputArea.append("📘 AI: $solution\n\n")
                                        }
                                    }
                                }
                            }
                        }
                    })
                }
            })
    }

    private fun querySolver(log: String): String? {
        return try {
            val serverUrl = MLoggerAISettings.getInstance().serverUrl
            val modelName = MLoggerAISettings.getInstance().modelName
            val accessToken = MLoggerAISettings.getInstance().systemServiceKey
            val systemPrompt = MLoggerAISettings.getInstance().systemPrompt

            val messagesArray = JSONArray().apply {
                // Add system message
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                // Add user message
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", log)
                })
            }

            val payload = JSONObject().apply {
                put("model", modelName)
                put("messages", messagesArray)
                put("temperature", 0.3)
                put("max_tokens", 150)
                put("stream", false)
            }

            val body: RequestBody = payload.toString()
                .toRequestBody("application/json".toMediaType())

            val request: Request = Request.Builder()
                .addHeader("Authorization", "Bearer $accessToken")
                .url(serverUrl)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                JSONObject(response.body!!.string()).getString("solution")
            } else {
                "Errore: Risposta non valida dal server"
            }
        } catch (e: Exception) {
            "Errore di comunicazione con ErrorSolver: ${e.message}"
        }
    }
}
