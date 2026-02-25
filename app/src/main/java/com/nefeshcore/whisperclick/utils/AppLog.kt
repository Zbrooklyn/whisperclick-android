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
    private var lastMessage: String? = null
    private var repeatCount = 0

    fun log(tag: String, msg: String) {
        Log.d(tag, msg)
        val key = "$tag: $msg"
        synchronized(this) {
            if (key == lastMessage) {
                repeatCount++
                val current = _log.value
                val lines = if (current.isEmpty()) mutableListOf() else current.lines().toMutableList()
                if (lines.isNotEmpty()) {
                    val timestamp = timeFormat.format(Date())
                    lines[lines.lastIndex] = "[$timestamp] $key (x$repeatCount)"
                    _log.value = lines.joinToString("\n")
                }
                return
            }
            lastMessage = key
            repeatCount = 1
            val timestamp = timeFormat.format(Date())
            val line = "[$timestamp] $key"
            val current = _log.value
            val lines = if (current.isEmpty()) mutableListOf() else current.lines().toMutableList()
            lines.add(line)
            while (lines.size > MAX_LINES) {
                lines.removeAt(0)
            }
            _log.value = lines.joinToString("\n")
        }
    }

    fun clear() {
        synchronized(this) {
            _log.value = ""
            lastMessage = null
            repeatCount = 0
        }
    }

    fun getFullLog(): String = _log.value
}
