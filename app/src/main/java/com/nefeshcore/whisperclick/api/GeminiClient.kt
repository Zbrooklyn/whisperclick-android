package com.nefeshcore.whisperclick.api

import com.nefeshcore.whisperclick.utils.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient : RewriteProvider {
    override val name = "Gemini"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    override suspend fun rewriteText(apiKey: String, originalText: String, style: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val promptText = when (style) {
                    "Emojify" -> "Rewrite the following text by adding relevant emojis. Keep the meaning the same. Output ONLY the rewritten text:\n\n$originalText"
                    "Fix Grammar" -> "Fix the grammar and spelling of the following text. Output ONLY the corrected text:\n\n$originalText"
                    "Concise" -> "Rewrite the following text to be more concise. Output ONLY the rewritten text:\n\n$originalText"
                    else -> "Rewrite the following text to be $style. Output ONLY the rewritten text:\n\n$originalText"
                }
                callApi(apiKey, promptText) ?: originalText
            } catch (e: Exception) {
                AppLog.log("Gemini", "ERROR: ${e.javaClass.simpleName}: ${e.message}")
                originalText
            }
        }
    }

    override suspend fun rewriteAll(apiKey: String, originalText: String): RewriteVariants {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """Rewrite the following text in 5 styles. Return ONLY valid JSON with no markdown formatting, no code fences, just the raw JSON object.

Keys: "clean" (fix spelling, grammar, punctuation only), "professional", "casual", "concise", "emojify" (add relevant emojis).

All style variants must be based on the clean version, not the original.

Text: "$originalText""""

                val response = callApi(apiKey, prompt)
                if (response != null) {
                    parseVariants(response, originalText)
                } else {
                    fallbackVariants(originalText)
                }
            } catch (e: Exception) {
                AppLog.log("Gemini", "rewriteAll ERROR: ${e.javaClass.simpleName}: ${e.message}")
                fallbackVariants(originalText)
            }
        }
    }

    private fun callApi(apiKey: String, promptText: String): String? {
        val url = URL(API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-goog-api-key", apiKey)
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.doOutput = true

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
        return if (responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseResponse(response)
        } else {
            val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "no body" } catch (_: Exception) { "unreadable" }
            AppLog.log("Gemini", "HTTP $responseCode: $errorBody")
            null
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
            null
        } ?: ""
    }

    private fun parseVariants(response: String, original: String): RewriteVariants {
        return try {
            // Strip markdown code fences if present
            val cleaned = response
                .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
                .replace(Regex("^```\\s*$", RegexOption.MULTILINE), "")
                .trim()
            val json = JSONObject(cleaned)
            RewriteVariants(
                clean = json.optString("clean", original),
                professional = json.optString("professional", original),
                casual = json.optString("casual", original),
                concise = json.optString("concise", original),
                emojify = json.optString("emojify", original)
            )
        } catch (e: Exception) {
            AppLog.log("Gemini", "Variant parse error: ${e.message}")
            fallbackVariants(original)
        }
    }

    private fun fallbackVariants(original: String) = RewriteVariants(
        clean = original, professional = original, casual = original,
        concise = original, emojify = original
    )
}
