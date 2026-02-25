package com.nefeshcore.whisperclick

import android.app.Application
import com.nefeshcore.whisperclick.utils.CrashLogger

class WhisperClickApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
