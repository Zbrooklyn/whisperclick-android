package com.nefeshcore.whisperclick.ui.main

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whispercpp.whisper.WhisperContext
import com.nefeshcore.whisperclick.R
import com.nefeshcore.whisperclick.utils.AppLog
import com.nefeshcore.whisperclick.media.decodeShortArray
import com.nefeshcore.whisperclick.media.encodeWaveFile
import com.nefeshcore.whisperclick.recorder.Recorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "MainScreenViewModel"

class MainScreenViewModel(private val application: Application) : ViewModel() {
    var canTranscribe by mutableStateOf(false)
        private set
    var dataLog by mutableStateOf("")
        private set
    var isRecording by mutableStateOf(false)
        private set
    private val sharedPref = application.getSharedPreferences(
        application.getString(R.string.preference_file_key), Context.MODE_PRIVATE
    )
    private val maxThreads = Runtime.getRuntime().availableProcessors()
    private var recorder: Recorder = Recorder()
    private var whisperContext: WhisperContext? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordedFile: File? = null

    init {
        viewModelScope.launch {
            printSystemInfo()
        }
    }

    private suspend fun printSystemInfo() {
        printMessage(String.format("System Info: %s\n", WhisperContext.getSystemInfo()))
    }

    private suspend fun ensureModelLoaded(): Boolean {
        if (whisperContext != null) return true
        printMessage("Loading model...\n")
        return try {
            loadBaseModel()
            canTranscribe = true
            true
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("Failed to load: ${e.localizedMessage}\n")
            false
        }
    }

    private suspend fun printMessage(msg: String) = withContext(Dispatchers.Main) {
        dataLog += msg
        if (dataLog.length > 10000) dataLog = dataLog.takeLast(8000)
        AppLog.log("Settings", msg.trimEnd('\n'))
    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        val models = application.assets.list("models/")
        if (models != null) {
            whisperContext =
                WhisperContext.createContextFromAsset(application.assets, "models/" + models[0])
            printMessage("Loaded model ${models[0]}.\n")
        }

        //val firstModel = modelsPath.listFiles()!!.first()
        //whisperContext = WhisperContext.createContextFromFile(firstModel.absolutePath)
    }

    fun benchmark() = viewModelScope.launch {
        if (!ensureModelLoaded()) return@launch
        val nThreads = sharedPref.getInt(application.getString(R.string.num_threads), maxThreads)
        runBenchmark(nThreads)
    }

    private suspend fun runBenchmark(nthreads: Int) {
        if (!canTranscribe) {
            return
        }

        canTranscribe = false

        printMessage("Running benchmark. This will take minutes...\n")
        whisperContext?.benchMemory(nthreads)?.let { printMessage(it) }
        printMessage("\n")
        whisperContext?.benchGgmlMulMat(nthreads)?.let { printMessage(it) }

        canTranscribe = true
    }


    private suspend fun stopPlayback() = withContext(Dispatchers.Main) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private suspend fun startPlayback(file: File) = withContext(Dispatchers.Main) {
        mediaPlayer = MediaPlayer.create(application, file.absolutePath.toUri())
        mediaPlayer?.start()
    }

    private suspend fun transcribeAudio(data: FloatArray) {
        val nThreads =
            sharedPref.getInt(application.getString(R.string.num_threads), maxThreads)
        printMessage("Transcribing data...\n")
        val start = System.currentTimeMillis()
        val text = whisperContext?.transcribeData(data, numThreads = nThreads)
        val elapsed = System.currentTimeMillis() - start
        printMessage("Done ($elapsed ms): \n$text\n")
    }

    fun toggleRecord() = viewModelScope.launch {
        try {
            if (isRecording) {
                val samples = recorder.stopRecording()
                isRecording = false

                if (samples.isNotEmpty() && canTranscribe) {
                    canTranscribe = false
                    try {
                        // Save WAV and play back
                        val file = recordedFile
                        if (file != null) {
                            withContext(Dispatchers.IO) { encodeWaveFile(file, samples) }
                            stopPlayback()
                            startPlayback(file)
                        }
                        // Transcribe directly from raw samples
                        val data = decodeShortArray(samples, 1)
                        printMessage("${data.size / (16000 / 1000)} ms\n")
                        transcribeAudio(data)
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, e)
                        printMessage("${e.localizedMessage}\n")
                    }
                    canTranscribe = true
                }
            } else {
                if (!ensureModelLoaded()) return@launch
                stopPlayback()
                val file = getTempFileForRecording()
                recorder.startRecording { e ->
                    viewModelScope.launch {
                        printMessage("${e.localizedMessage}\n")
                        isRecording = false
                    }
                }
                isRecording = true
                recordedFile = file
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            isRecording = false
        }
    }

    private suspend fun getTempFileForRecording() = withContext(Dispatchers.IO) {
        File.createTempFile("recording", "wav")
    }

    override fun onCleared() {
        // Release on background thread — never block main thread (causes ANR)
        val ctx = whisperContext
        whisperContext = null
        CoroutineScope(Dispatchers.Default).launch { ctx?.release() }
        mediaPlayer?.release()
        mediaPlayer = null
        recorder.release()
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainScreenViewModel(application)
            }
        }
    }
}
