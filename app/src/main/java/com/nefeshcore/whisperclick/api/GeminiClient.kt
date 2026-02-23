package com.nefeshcore.whisperclick.api

import android.util.Log
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

                val prompt = "Rewrite the following text to be $style. Output ONLY the rewritten text:

$originalText"
                
                val jsonBody = JSONObject()
                val contents = JSONArray()
                val contentPart = JSONObject()
                val parts = JSONArray()
                val textPart = JSONObject()
                
                textPart.put("text", prompt)
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
                    Log.e(TAG, "Error: $responseCode")
                    originalText // Return original on error
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
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
            Log.e(TAG, "Parse Error: ${e.message}")
            ""
        }
    }
}
