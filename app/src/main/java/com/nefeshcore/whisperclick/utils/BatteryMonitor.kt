package com.nefeshcore.whisperclick.utils

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.util.Log

object BatteryMonitor {
    private const val TAG = "BatteryMonitor"
    private var startTime: Long = 0
    private var startLevel: Int = 0

    fun startMonitoring(context: Context) {
        startTime = SystemClock.elapsedRealtime()
        startLevel = getBatteryLevel(context)
        Log.d(TAG, "Battery Monitoring Started: Level=$startLevel%")
    }

    fun stopMonitoring(context: Context): String {
        val endTime = SystemClock.elapsedRealtime()
        val endLevel = getBatteryLevel(context)
        val durationSec = (endTime - startTime) / 1000
        val drop = startLevel - endLevel
        
        val report = """Battery Usage Report:
Duration: ${durationSec}s
Drop: $drop%
Start: $startLevel%
End: $endLevel%""".trimIndent()
        Log.d(TAG, report)
        return report
    }

    private fun getBatteryLevel(context: Context): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
