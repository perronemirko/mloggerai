package com.mloggerai.plugin

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextArea
import com.intellij.util.messages.MessageBusConnection
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.OkHttpClient


object ErrorSolverService {
    private lateinit var connection: MessageBusConnection
    private val client = OkHttpClient()

    fun init(project: Project, outputArea: JBTextArea) {
        connection = project.messageBus.connect()
        connection.subscribe(
            com.intellij.execution.ExecutionManager.EXECUTION_TOPIC,
            object : ExecutionListener {
                override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
                    handler.addProcessListener(object : ProcessAdapter() {
                        override fun onTextAvailable(
                            event: ProcessEvent,
                            outputType: com.intellij.openapi.util.Key<*>
                        ) {
                            val logLine = event.text.trim()
                            if (logLine.isNotEmpty()) {
                                val solution = querySolver(logLine)
                                if (solution != null) {
                                    outputArea.append("⚡ Log: $logLine\n")
                                    outputArea.append("📘 AI: $solution\n\n")
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

            val terminalError = ""
            val messagesArray = JSONArray()

// Add system message
            val systemMessage = JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            }
            messagesArray.put(systemMessage)

// Add user message
            val userInput = JSONObject().apply {
                put("role", "user")
                put("content", terminalError)
            }
            messagesArray.put(userInput)

// Costruzione del payload JSON
            val payload = JSONObject().apply {
                put("model", modelName)
                put("messages", messagesArray)
                put("temperature", 0.3)
                put("max_tokens", 150)
                put("stream", false)
            }

// Creazione del RequestBody moderno
            val body: RequestBody = payload.toString()
                .toRequestBody("application/json".toMediaType())

// Creazione della request
            val request: Request = Request.Builder()
                .addHeader("Authorization", "Bearer $accessToken")
                .url(serverUrl)
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                JSONObject(response.body!!.string()).getString("solution")
            } else null
        } catch (e: Exception) {
            "Errore di comunicazione con ErrorSolver: ${e.message}"
        }
    }
}
