package com.nefeshcore.whisperclick

import android.content.Context
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.whispercpp.whisper.WhisperContext
import com.nefeshcore.whisperclick.media.decodeShortArray
import com.nefeshcore.whisperclick.recorder.Recorder
import com.nefeshcore.whisperclick.utils.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "VoiceKeyboardInputMethodService"

class VoiceKeyboardInputMethodService : InputMethodService(), LifecycleOwner,
    SavedStateRegistryOwner {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    // handle audio manager
    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(LOG_TAG, "loss of focus")
                toggleRecord()
            }
        }
    }

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        ).setAcceptsDelayedFocusGain(false).setOnAudioFocusChangeListener(focusChangeListener)
            .build()

    private var currentEditorInfo: EditorInfo? = null

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        currentEditorInfo = editorInfo
        // Reload model if it was released when keyboard was hidden
        if (whisperContext == null && !isModelLoading) {
            isModelLoading = true
            scope.launch {
                try {
                    loadBaseModel()
                    canTranscribe = true
                } catch (e: Exception) {
                    Log.w(LOG_TAG, "Failed to reload model", e)
                }
                isModelLoading = false
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Unload model from RAM when keyboard is hidden
        if (!isRecording && !isTranscribing) {
            scope.launch {
                whisperContext?.release()
                whisperContext = null
                canTranscribe = false
                AppLog.log("Keyboard", "Model unloaded (keyboard hidden)")
            }
        }
    }

    override fun onCreateInputView(): View {
        val view = VoiceKeyboardView(this)
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
        return view
    }

    // Lifecycle Methods
    private var lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private fun handleLifecycleEvent(event: Lifecycle.Event) =
        lifecycleRegistry.handleLifecycleEvent(event)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryCtrl.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        modelsPath = File(application.filesDir, "models")
        samplesPath = File(application.filesDir, "samples")
        sharedPref = application.getSharedPreferences(
            getString(R.string.preference_file_key), Context.MODE_PRIVATE
        )

        useCloudStt = sharedPref?.getString("stt_mode", "local") == "cloud"

        scope.launch {
            printSystemInfo()
            loadData()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release whisper context on its own executor without blocking main thread
        val ctx = whisperContext
        whisperContext = null
        if (ctx != null) {
            CoroutineScope(Dispatchers.Default).launch { ctx.release() }
        }
        job.cancel()
        handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    // --- Magic Rewrite Feature ---
    fun performRewrite(style: String) {
        AppLog.log("Rewrite", "Triggered: style=$style")

        val ic = currentInputConnection ?: run {
            AppLog.log("Rewrite", "ERROR: No InputConnection")
            return
        }
        val textBefore = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""

        if (textBefore.isEmpty()) {
            AppLog.log("Rewrite", "No text to rewrite")
            return
        }
        AppLog.log("Rewrite", "Input: ${textBefore.take(80)}${if (textBefore.length > 80) "..." else ""}")

        scope.launch {
            val provider = sharedPref?.getString("ai_provider", "gemini") ?: "gemini"
            val apiKeyPref = if (provider == "openai") "openai_api_key" else "gemini_api_key"
            val apiKey = sharedPref?.getString(apiKeyPref, "") ?: ""
            val client: com.nefeshcore.whisperclick.api.RewriteProvider = if (provider == "openai") {
                com.nefeshcore.whisperclick.api.OpenAIClient
            } else {
                com.nefeshcore.whisperclick.api.GeminiClient
            }

            if (apiKey.isEmpty()) {
                val providerName = client.name
                AppLog.log("Rewrite", "ERROR: $providerName API Key not set")
                withContext(Dispatchers.Main) {
                    ic.commitText("[Error: Set $providerName API Key in Settings]", 1)
                }
                return@launch
            }

            AppLog.log("Rewrite", "Calling ${client.name} API...")
            val start = System.currentTimeMillis()
            val rewritten = client.rewriteText(apiKey, textBefore, style)
            val elapsed = System.currentTimeMillis() - start

            if (rewritten.isNotEmpty() && rewritten != textBefore) {
                AppLog.log("Rewrite", "Done (${elapsed}ms): ${rewritten.take(80)}${if (rewritten.length > 80) "..." else ""}")
                withContext(Dispatchers.Main) {
                    ic.deleteSurroundingText(textBefore.length, 0)
                    ic.commitText(rewritten, 1)
                }
            } else {
                AppLog.log("Rewrite", "No change returned (${elapsed}ms)")
            }
        }
    }
    // -----------------------------

    // SaveStateRegistry Methods
    private val savedStateRegistryCtrl = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryCtrl.savedStateRegistry

    // copy whispercppdemo MainScreenViewModel implementation
    private var whisperContext: WhisperContext? = null
    private var recorder: Recorder = Recorder()
    var canTranscribe by mutableStateOf(false)
        private set
    var isRecording by mutableStateOf(false)
        private set
    var isTranscribing by mutableStateOf(false)
        private set
    @Volatile
    private var transcriptionCancelled = false
    private var isModelLoading = false
    var useCloudStt by mutableStateOf(false)
        private set
    private var modelsPath: File? = null
    private var samplesPath: File? = null
    private var sharedPref: SharedPreferences? = null
    private val maxThreads = Runtime.getRuntime().availableProcessors()
    private var trailing: String? = null

    private fun checkBoolPref(resource: Int): Boolean? {
        return sharedPref?.getBoolean(
            application.getString(resource), false
        )
    }

    private fun printSystemInfo() {
        printMessage(String.format("System Info: %s\n", WhisperContext.getSystemInfo()))
    }

    private suspend fun loadData() {
        printMessage("Loading data...\n")
        try {
            copyAssets()
            loadBaseModel()
            canTranscribe = true
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }
    }

    private fun printMessage(msg: String) {
        AppLog.log("Keyboard", msg.trimEnd('\n'))
    }

    private suspend fun copyAssets() = withContext(Dispatchers.IO) {
        modelsPath?.mkdirs()
        samplesPath?.mkdirs()
        //application.copyData("models", modelsPath, ::printMessage)
        //samplesPath?.let { application.copyData("samples", it, ::printMessage) }
        printMessage("All data copied to working directory.\n")
    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        printMessage("Loading model...\n")
        val activeModel = sharedPref?.getString("active_model", "") ?: ""
        val modelsDir = File(application.filesDir, "models")

        // Try active model from downloads first
        if (activeModel.isNotEmpty()) {
            val downloadedFile = File(modelsDir, activeModel)
            if (downloadedFile.exists()) {
                whisperContext = WhisperContext.createContextFromFile(downloadedFile.absolutePath)
                printMessage("Loaded model $activeModel.\n")
                return@withContext
            }
        }

        // Fall back to bundled asset models
        val assetModels = application.assets.list("models/")
        if (assetModels != null && assetModels.isNotEmpty()) {
            val modelName = if (activeModel.isNotEmpty() && activeModel in assetModels) activeModel else assetModels[0]
            whisperContext = WhisperContext.createContextFromAsset(application.assets, "models/$modelName")
            printMessage("Loaded model $modelName.\n")
        } else {
            throw Exception("no models found")
        }
    }

    private suspend fun transcribe(samples: ShortArray): String? {
        if (samples.isEmpty()) return null
        val data = decodeShortArray(samples, 1)
        val nThreads =
            sharedPref?.getInt(application.getString(R.string.num_threads), maxThreads)
        val durationMs = samples.size / (16000 / 1000)
        printMessage("Transcribing ${durationMs}ms of audio...\n")
        val start = System.currentTimeMillis()
        var text = whisperContext?.transcribeData(data, false, nThreads ?: maxThreads)?.trim()
            ?: return null
        // remove special tokens like [MUSIC] or [BLANK_AUDIO]
        text = text.replace(Regex("""\[[-_a-zA-Z0-9 ]*]"""), "")
        text = text.replace(Regex("""\*[-_a-zA-Z0-9 ]*\*"""), "")
        text = text.replace(Regex("""\([-_a-zA-Z0-9 ]*\)"""), "")
        if (checkBoolPref(R.string.casual_mode) == true) {
            text = text.trim().lowercase()
            if (text.isNotEmpty() && text.last() in charArrayOf('.', ',', ';')) {
                text = text.dropLast(1)
            }
        }
        val elapsed = System.currentTimeMillis() - start
        printMessage("Done (${elapsed}ms): $text\n")
        return text.ifEmpty { null }
    }

    fun cancelTranscription() {
        transcriptionCancelled = true
        AppLog.log("Keyboard", "Transcription cancel requested")
    }

    fun cancelRecord() = scope.launch {
        try {
            if (isRecording) {
                if (checkBoolPref(R.string.pause_media) == true) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
                recorder.stopRecording()
                currentInputConnection?.finishComposingText()
                isRecording = false
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            isRecording = false
        }
    }

    fun toggleRecord() = scope.launch {
        try {
            val ic = currentInputConnection
            if (isRecording) {
                AppLog.log("Keyboard", "Recording stopped")
                if (checkBoolPref(R.string.pause_media) == true) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
                val samples = recorder.stopRecording()
                isRecording = false

                if (samples.isNotEmpty() && (canTranscribe || useCloudStt)) {
                    canTranscribe = false
                    isTranscribing = true
                    transcriptionCancelled = false
                    ic?.setComposingText("Transcribing...", 1)
                    val text = if (useCloudStt) transcribeCloud(samples) else transcribe(samples)
                    isTranscribing = false
                    if (transcriptionCancelled) {
                        AppLog.log("Keyboard", "Transcription cancelled")
                        ic?.setComposingText("", 1)
                    } else if (text != null) {
                        ic?.setComposingText(text + (trailing ?: ""), 1)
                    }
                    canTranscribe = true
                }
                ic?.finishComposingText()
            } else {
                if (checkBoolPref(R.string.pause_media) == true) {
                    val result = audioManager.requestAudioFocus(focusRequest)
                    if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        Log.w(LOG_TAG, "Failed to gain audio focus")
                        return@launch
                    }
                }
                trailing = null
                recorder.startRecording { e ->
                    AppLog.log("Keyboard", "Recording error: ${e.localizedMessage}")
                    scope.launch { isRecording = false }
                }
                AppLog.log("Keyboard", "Recording started")
                isRecording = true
                // if not preceded by empty space, commit empty space
                val charBefore = ic?.getTextBeforeCursor(1, 0)
                if (charBefore?.isNotEmpty()?.and(charBefore != " ") == true) {
                    ic?.commitText(" ", 1)
                }
                // if not followed by empty space, add trailing space
                val charAfter = ic?.getTextAfterCursor(1, 0)
                if (charAfter != " ") {
                    trailing = " "
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            isRecording = false
        }
    }

    // Haptic feedback
    fun haptic() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // for keys — supports optional meta keys (e.g. CTRL for undo)
    fun sendKeyPress(key: Int, meta: Int = 0) {
        haptic()
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(
            KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0, meta)
        )
        ic.sendKeyEvent(
            KeyEvent(0, 0, KeyEvent.ACTION_UP, key, 0, meta)
        )
    }

    // Enter key that respects imeOptions (Send, Search, Go, Next, Done)
    fun sendEnter() {
        haptic()
        val ei = currentEditorInfo
        val ic = currentInputConnection ?: return
        if (ei != null) {
            val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
                return
            }
        }
        sendKeyPress(KeyEvent.KEYCODE_ENTER)
    }

    fun performEditAction(actionId: Int) {
        haptic()
        currentInputConnection?.performContextMenuAction(actionId)
    }

    fun shouldRenderSwitcher(): Boolean {
        return shouldOfferSwitchingToNextInputMethod()
    }

    fun switchKeyboard() {
        haptic()
        val preferredIme = sharedPref?.getString("preferred_keyboard", "") ?: ""
        if (preferredIme.isNotEmpty()) {
            switchInputMethod(preferredIme)
        } else {
            switchToNextInputMethod(false)
        }
    }

    fun openSettings() {
        val intent = android.content.Intent(this, MainActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    fun toggleSttMode() {
        haptic()
        useCloudStt = !useCloudStt
        sharedPref?.edit()?.putString("stt_mode", if (useCloudStt) "cloud" else "local")?.apply()
        val mode = if (useCloudStt) "Cloud (OpenAI)" else "Local Whisper"
        AppLog.log("Keyboard", "STT mode: $mode")
    }

    // Cloud STT via OpenAI Whisper API
    private suspend fun transcribeCloud(samples: ShortArray): String? {
        if (samples.isEmpty()) return null
        val apiKey = sharedPref?.getString("openai_api_key", "") ?: ""
        if (apiKey.isEmpty()) {
            printMessage("Cloud STT requires OpenAI API key\n")
            return null
        }
        val durationMs = samples.size / (16000 / 1000)
        printMessage("Cloud transcribing ${durationMs}ms...\n")
        val start = System.currentTimeMillis()
        val result = com.nefeshcore.whisperclick.api.WhisperCloudClient.transcribe(apiKey, samples)
        val elapsed = System.currentTimeMillis() - start
        if (result != null) {
            printMessage("Cloud done (${elapsed}ms): $result\n")
        } else {
            printMessage("Cloud STT failed\n")
        }
        return result
    }
}

private suspend fun Context.copyData(
    assetDirName: String, destDir: File, printMessage: suspend (String) -> Unit
) = withContext(Dispatchers.IO) {
    assets.list(assetDirName)?.forEach { name ->
        val assetPath = "$assetDirName/$name"
        Log.v(LOG_TAG, "Processing $assetPath...")
        val destination = File(destDir, name)
        Log.v(LOG_TAG, "Copying $assetPath to $destination...")
        printMessage("Copying $name...\n")
        assets.open(assetPath).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.v(LOG_TAG, "Copied $assetPath to $destination")
    }
}