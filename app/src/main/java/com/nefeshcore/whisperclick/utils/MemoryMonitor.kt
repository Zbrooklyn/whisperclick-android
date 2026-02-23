package com.nefeshcore.whisperclick.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log

object MemoryMonitor {
    private const val TAG = "MemoryMonitor"

    fun getMemoryUsage(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val nativeHeapSize = Debug.getNativeHeapSize() / 1024 / 1024
        val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize() / 1024 / 1024
        val nativeHeapFree = Debug.getNativeHeapFreeSize() / 1024 / 1024

        val report = """
            Memory Usage:
            Total RAM: ${memoryInfo.totalMem / 1024 / 1024} MB
            Available RAM: ${memoryInfo.availMem / 1024 / 1024} MB
            Native Heap: ${nativeHeapAllocated}MB / ${nativeHeapSize}MB
        """.trimIndent()
        
        Log.d(TAG, report)
        return report
    }
}
