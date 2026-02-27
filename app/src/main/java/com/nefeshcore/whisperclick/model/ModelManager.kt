package com.nefeshcore.whisperclick.model

import android.content.Context
import com.nefeshcore.whisperclick.R
import com.nefeshcore.whisperclick.utils.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class WhisperModel(
    val name: String,
    val fileName: String,
    val url: String,
    val sizeMb: Int,
    val tier: String  // "Tiny", "Base", "Small", "Medium"
)

data class DownloadProgress(
    val model: WhisperModel? = null,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val isDownloading: Boolean = false,
)

object ModelManager {
    private const val HF_BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

    // Q5_1 quantized models — 60% smaller, faster inference, near-identical accuracy
    val availableModels = listOf(
        WhisperModel("Tiny English (Q5)", "ggml-tiny.en-q5_1.bin", "$HF_BASE/ggml-tiny.en-q5_1.bin", 31, "Tiny"),
        WhisperModel("Base English (Q5)", "ggml-base.en-q5_1.bin", "$HF_BASE/ggml-base.en-q5_1.bin", 57, "Base"),
        WhisperModel("Small English (Q5)", "ggml-small.en-q5_1.bin", "$HF_BASE/ggml-small.en-q5_1.bin", 181, "Small"),
        WhisperModel("Medium English (Q5)", "ggml-medium.en-q5_1.bin", "$HF_BASE/ggml-medium.en-q5_1.bin", 514, "Medium"),
        WhisperModel("Tiny Multilingual (Q5)", "ggml-tiny-q5_1.bin", "$HF_BASE/ggml-tiny-q5_1.bin", 31, "Tiny"),
        WhisperModel("Base Multilingual (Q5)", "ggml-base-q5_1.bin", "$HF_BASE/ggml-base-q5_1.bin", 57, "Base"),
        WhisperModel("Small Multilingual (Q5)", "ggml-small-q5_1.bin", "$HF_BASE/ggml-small-q5_1.bin", 181, "Small"),
    )

    private val _progress = MutableStateFlow(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = _progress

    @Volatile
    private var cancelRequested = false

    fun getModelsDir(context: Context): File {
        return File(context.filesDir, "models").also { it.mkdirs() }
    }

    fun getDownloadedModels(context: Context): List<String> {
        val dir = getModelsDir(context)
        return dir.listFiles()
            ?.filter { !it.name.endsWith(".tmp") }
            ?.map { it.name }
            ?: emptyList()
    }

    fun getBundledModels(context: Context): List<String> {
        return context.assets.list("models/")?.toList() ?: emptyList()
    }

    private fun getPrefs(context: Context) = context.getSharedPreferences(
        context.getString(R.string.preference_file_key), Context.MODE_PRIVATE
    )

    fun getActiveModelName(context: Context): String {
        return getPrefs(context).getString("active_model", "") ?: ""
    }

    fun setActiveModel(context: Context, modelFileName: String) {
        getPrefs(context).edit().putString("active_model", modelFileName).apply()
    }

    fun deleteModel(context: Context, fileName: String): Boolean {
        val file = File(getModelsDir(context), fileName)
        return if (file.exists()) file.delete() else false
    }

    fun cancelDownload() {
        cancelRequested = true
    }

    suspend fun downloadModel(context: Context, model: WhisperModel): Boolean = withContext(Dispatchers.IO) {
        cancelRequested = false
        _progress.value = DownloadProgress(model, 0, 0, true)
        AppLog.log("Model", "Downloading ${model.name} (${model.sizeMb}MB)...")

        val destFile = File(getModelsDir(context), model.fileName)
        val tempFile = File(getModelsDir(context), "${model.fileName}.tmp")

        try {
            val url = URL(model.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            // Resume support
            if (tempFile.exists()) {
                connection.setRequestProperty("Range", "bytes=${tempFile.length()}-")
            }

            connection.connect()
            val responseCode = connection.responseCode

            val totalBytes = if (responseCode == 206) {
                // Partial content — get full size from Content-Range
                val range = connection.getHeaderField("Content-Range") ?: ""
                val total = range.substringAfter("/", "").toLongOrNull()
                    ?: (tempFile.length() + connection.contentLength.toLong())
                total
            } else {
                if (tempFile.exists()) tempFile.delete() // Can't resume, start over
                connection.contentLength.toLong()
            }

            _progress.value = DownloadProgress(model, tempFile.length(), totalBytes, true)

            val input = connection.inputStream
            val output = if (responseCode == 206) {
                java.io.FileOutputStream(tempFile, true)  // append for resume
            } else {
                tempFile.outputStream()  // overwrite for fresh download
            }

            val buffer = ByteArray(8192)
            var downloaded = tempFile.length()
            output.use { out ->
                while (true) {
                    if (cancelRequested) {
                        AppLog.log("Model", "Download cancelled")
                        _progress.value = DownloadProgress()
                        return@withContext false
                    }
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    _progress.value = DownloadProgress(model, downloaded, totalBytes, true)
                }
            }
            input.close()

            if (!tempFile.renameTo(destFile)) {
                throw java.io.IOException("Failed to move downloaded file to ${destFile.absolutePath}")
            }
            _progress.value = DownloadProgress()
            AppLog.log("Model", "${model.name} downloaded successfully")
            true
        } catch (e: Exception) {
            AppLog.log("Model", "Download failed: ${e.message}")
            _progress.value = DownloadProgress()
            false
        }
    }
}
