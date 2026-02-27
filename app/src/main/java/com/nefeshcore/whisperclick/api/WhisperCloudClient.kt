package com.nefeshcore.whisperclick.api

import com.nefeshcore.whisperclick.media.encodeWaveBytes
import com.nefeshcore.whisperclick.utils.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

object WhisperCloudClient {
    private const val API_URL = "https://api.openai.com/v1/audio/transcriptions"

    suspend fun transcribe(apiKey: String, samples: ShortArray): String? = withContext(Dispatchers.IO) {
        val wavBytes = encodeWaveBytes(samples)
        val boundary = "----WhisperClick${System.currentTimeMillis()}"

        val url = URL(API_URL)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            DataOutputStream(connection.outputStream).use { out ->
                // file field
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n")
                out.writeBytes("Content-Type: audio/wav\r\n\r\n")
                out.write(wavBytes)
                out.writeBytes("\r\n")

                // model field
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
                out.writeBytes("whisper-1\r\n")

                // response_format field
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n")
                out.writeBytes("json\r\n")

                out.writeBytes("--$boundary--\r\n")
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                json.getString("text").trim().ifEmpty { null }
            } else {
                val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "no body" } catch (_: Exception) { "unreadable" }
                AppLog.log("CloudSTT", "HTTP $responseCode: $errorBody")
                null
            }
        } catch (e: Exception) {
            AppLog.log("CloudSTT", "ERROR: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

}
