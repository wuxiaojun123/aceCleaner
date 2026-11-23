package com.nice.library_float

import android.app.ActivityManager
import android.content.Context


object MemoryUsageUtils {
    data class Snapshot(
        val usedBytes: Long,
        val totalBytes: Long,
        val usedPercent: Int,
    )

    fun getSnapshot(context: Context): Snapshot {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activityManager?.getMemoryInfo(memoryInfo)

        val totalBytes = memoryInfo.totalMem.coerceAtLeast(0L)
        val usedBytes = (totalBytes - memoryInfo.availMem).coerceIn(0L, totalBytes)
        val usedPercent = if (totalBytes > 0L) {
            ((usedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        return Snapshot(usedBytes, totalBytes, usedPercent)
    }

    fun getMemoryUsedPercent(context: Context): Int = getSnapshot(context).usedPercent
}
