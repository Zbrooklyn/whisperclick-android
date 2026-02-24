package com.nefeshcore.whisperclick.api

import com.nefeshcore.whisperclick.utils.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    suspend fun rewriteText(apiKey: String, originalText: String, style: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$API_URL?key=$apiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val promptText = when (style) {
                    "Emojify" -> "Rewrite the following text by adding relevant emojis. Keep the meaning the same. Output ONLY the rewritten text:\n\n$originalText"
                    "Fix Grammar" -> "Fix the grammar and spelling of the following text. Output ONLY the corrected text:\n\n$originalText"
                    "Concise" -> "Rewrite the following text to be more concise. Output ONLY the rewritten text:\n\n$originalText"
                    else -> "Rewrite the following text to be $style. Output ONLY the rewritten text:\n\n$originalText"
                }

                val jsonBody = JSONObject()
                val contents = JSONArray()
                val contentPart = JSONObject()
                val parts = JSONArray()
                val textPart = JSONObject()
                
                textPart.put("text", promptText)
                parts.put(textPart)
                contentPart.put("parts", parts)
                contents.put(contentPart)
                jsonBody.put("contents", contents)

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonBody.toString())
                writer.flush()

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseResponse(response)
                } else {
                    val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "no body" } catch (_: Exception) { "unreadable" }
                    AppLog.log("Gemini", "HTTP $responseCode: $errorBody")
                    originalText
                }
            } catch (e: Exception) {
                AppLog.log("Gemini", "ERROR: ${e.javaClass.simpleName}: ${e.message}")
                originalText
            }
        }
    }

    private fun parseResponse(jsonResponse: String): String {
        return try {
            val json = JSONObject(jsonResponse)
            val candidates = json.getJSONArray("candidates")
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            parts.getJSONObject(0).getString("text").trim()
        } catch (e: Exception) {
            AppLog.log("Gemini", "Parse error: ${e.message}")
            ""
        }
    }
}
