package com.nefeshcore.whisperclick.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ApiKeyValidator {

    sealed class Result {
        data object Valid : Result()
        data class Invalid(val message: String) : Result()
    }

    suspend fun validateOpenAI(apiKey: String): Result = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.Invalid("Key is empty")
        if (!apiKey.startsWith("sk-")) return@withContext Result.Invalid("Should start with sk-")
        try {
            val url = URL("https://api.openai.com/v1/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val code = conn.responseCode
            conn.disconnect()
            if (code == 200) Result.Valid
            else Result.Invalid("HTTP $code — check your key")
        } catch (e: Exception) {
            Result.Invalid(e.message ?: "Connection failed")
        }
    }

    suspend fun validateGemini(apiKey: String): Result = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.Invalid("Key is empty")
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val code = conn.responseCode
            conn.disconnect()
            if (code == 200) Result.Valid
            else Result.Invalid("HTTP $code — check your key")
        } catch (e: Exception) {
            Result.Invalid(e.message ?: "Connection failed")
        }
    }
}
