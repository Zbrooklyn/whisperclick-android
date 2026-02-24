package com.nefeshcore.whisperclick.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val MAX_LINES = 200
    private val _log = MutableStateFlow("")
    val log: StateFlow<String> = _log.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(tag: String, msg: String) {
        Log.d(tag, msg)
        val timestamp = timeFormat.format(Date())
        val line = "[$timestamp] $tag: $msg"
        synchronized(this) {
            val current = _log.value
            val lines = if (current.isEmpty()) mutableListOf() else current.lines().toMutableList()
            lines.add(line)
            // Keep only the most recent lines
            while (lines.size > MAX_LINES) {
                lines.removeAt(0)
            }
            _log.value = lines.joinToString("\n")
        }
    }

    fun clear() {
        _log.value = ""
    }

    fun getFullLog(): String = _log.value
}
