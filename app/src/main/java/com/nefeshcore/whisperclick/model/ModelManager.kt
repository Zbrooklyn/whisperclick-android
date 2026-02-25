package com.nefeshcore.whisperclick.model

import android.content.Context
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

    val availableModels = listOf(
        WhisperModel("Tiny English", "ggml-tiny.en.bin", "$HF_BASE/ggml-tiny.en.bin", 75, "Tiny"),
        WhisperModel("Base English", "ggml-base.en.bin", "$HF_BASE/ggml-base.en.bin", 142, "Base"),
        WhisperModel("Small English", "ggml-small.en.bin", "$HF_BASE/ggml-small.en.bin", 466, "Small"),
        WhisperModel("Medium English", "ggml-medium.en.bin", "$HF_BASE/ggml-medium.en.bin", 1500, "Medium"),
        WhisperModel("Tiny Multilingual", "ggml-tiny.bin", "$HF_BASE/ggml-tiny.bin", 75, "Tiny"),
        WhisperModel("Base Multilingual", "ggml-base.bin", "$HF_BASE/ggml-base.bin", 142, "Base"),
        WhisperModel("Small Multilingual", "ggml-small.bin", "$HF_BASE/ggml-small.bin", 466, "Small"),
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
        return dir.listFiles()?.map { it.name } ?: emptyList()
    }

    fun getBundledModels(context: Context): List<String> {
        return context.assets.list("models/")?.toList() ?: emptyList()
    }

    fun getActiveModelName(context: Context): String {
        val prefs = context.getSharedPreferences("com.nefeshcore.whisperclick.settings", Context.MODE_PRIVATE)
        return prefs.getString("active_model", "") ?: ""
    }

    fun setActiveModel(context: Context, modelFileName: String) {
        val prefs = context.getSharedPreferences("com.nefeshcore.whisperclick.settings", Context.MODE_PRIVATE)
        prefs.edit().putString("active_model", modelFileName).apply()
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
            val output = tempFile.outputStream().let {
                if (responseCode == 206) java.io.FileOutputStream(tempFile, true) else it
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

            tempFile.renameTo(destFile)
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
