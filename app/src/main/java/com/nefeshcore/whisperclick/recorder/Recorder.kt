package com.nefeshcore.whisperclick.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class Recorder {
    private val scope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private var thread: AudioCaptureThread? = null

    suspend fun startRecording(onError: (Exception) -> Unit) = withContext(scope.coroutineContext) {
        thread = AudioCaptureThread(onError)
        thread?.start()
    }

    suspend fun stopRecording(): ShortArray = withContext(scope.coroutineContext) {
        val t = thread ?: return@withContext ShortArray(0)
        t.requestStop()
        @Suppress("BlockingMethodInNonBlockingContext")
        t.join()
        thread = null
        t.getSamples()
    }
}

private class AudioCaptureThread(
    private val onError: (Exception) -> Unit
) : Thread("AudioCapture") {
    private val quit = AtomicBoolean(false)
    private val chunks = mutableListOf<ShortArray>()
    private var totalSamples = 0

    @SuppressLint("MissingPermission")
    override fun run() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 4
            val buffer = ShortArray(bufferSize / 2)

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            try {
                audioRecord.startRecording()
                while (!quit.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        chunks.add(buffer.copyOfRange(0, read))
                        totalSamples += read
                    } else {
                        throw RuntimeException("audioRecord.read returned $read")
                    }
                }
                audioRecord.stop()
            } finally {
                audioRecord.release()
            }
        } catch (e: Exception) {
            if (!quit.get()) onError(e)
        }
    }

    fun requestStop() {
        quit.set(true)
    }

    fun getSamples(): ShortArray {
        val result = ShortArray(totalSamples)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    companion object {
        const val SAMPLE_RATE = 16000
    }
}
