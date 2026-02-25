package com.nefeshcore.whisperclick.utils

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val DIR_NAME = "crash_logs"
    private const val MAX_CRASHES = 10

    private var crashDir: File? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        crashDir = File(context.filesDir, DIR_NAME).also { it.mkdirs() }
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrash(thread, throwable)
            // Chain to default handler (shows system crash dialog / kills process)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        try {
            val dir = crashDir ?: return
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(dir, "crash_$timestamp.txt")

            val sw = StringWriter()
            val pw = PrintWriter(sw)

            pw.println("=== WhisperClick Crash Report ===")
            pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            pw.println("Thread: ${thread.name} (id=${thread.id})")
            pw.println()
            pw.println("--- Exception ---")
            throwable.printStackTrace(pw)
            pw.println()

            // Snapshot the in-memory AppLog
            val appLog = try { AppLog.getFullLog() } catch (_: Exception) { "" }
            if (appLog.isNotEmpty()) {
                pw.println("--- App Log (last 200 lines) ---")
                pw.println(appLog)
            }

            pw.flush()
            file.writeText(sw.toString())

            // Prune old crash files
            val files = dir.listFiles()?.sortedByDescending { it.name } ?: return
            files.drop(MAX_CRASHES).forEach { it.delete() }
        } catch (_: Exception) {
            // Can't do much if crash logging itself fails
        }
    }

    fun getCrashFiles(): List<File> {
        val dir = crashDir ?: return emptyList()
        return dir.listFiles()?.sortedByDescending { it.name }?.toList() ?: emptyList()
    }

    fun getLatestCrash(): String? {
        return getCrashFiles().firstOrNull()?.readText()
    }

    fun getCrashCount(): Int = getCrashFiles().size

    fun clearAll() {
        crashDir?.listFiles()?.forEach { it.delete() }
    }
}
