package com.nefeshcore.whisperclick.api

import com.nefeshcore.whisperclick.utils.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object OpenAIClient : RewriteProvider {
    override val name = "OpenAI"
    private const val API_URL = "https://api.openai.com/v1/chat/completions"

    override suspend fun rewriteText(apiKey: String, originalText: String, style: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.doOutput = true

                val systemPrompt = "You are a writing assistant. Rewrite the user's text according to the requested style. Output ONLY the rewritten text. No preamble."

                val userPrompt = when (style) {
                    "Emojify" -> "Rewrite the following text by adding relevant emojis. Keep the meaning the same. Output ONLY the rewritten text:\n\n$originalText"
                    "Fix Grammar" -> "Fix the grammar and spelling of the following text. Output ONLY the corrected text:\n\n$originalText"
                    "Concise" -> "Rewrite the following text to be more concise. Output ONLY the rewritten text:\n\n$originalText"
                    else -> "Rewrite the following text to be $style. Output ONLY the rewritten text:\n\n$originalText"
                }

                val messages = JSONArray()
                messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
                messages.put(JSONObject().put("role", "user").put("content", userPrompt))

                val jsonBody = JSONObject()
                jsonBody.put("model", "gpt-4o-mini")
                jsonBody.put("messages", messages)

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonBody.toString())
                writer.flush()

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseResponse(response)
                } else {
                    val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "no body" } catch (_: Exception) { "unreadable" }
                    AppLog.log("OpenAI", "HTTP $responseCode: $errorBody")
                    originalText
                }
            } catch (e: Exception) {
                AppLog.log("OpenAI", "ERROR: ${e.javaClass.simpleName}: ${e.message}")
                originalText
            }
        }
    }

    private fun parseResponse(jsonResponse: String): String {
        return try {
            val json = JSONObject(jsonResponse)
            val choices = json.getJSONArray("choices")
            choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
        } catch (e: Exception) {
            AppLog.log("OpenAI", "Parse error: ${e.message}")
            ""
        }
    }
}
