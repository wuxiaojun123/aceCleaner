package com.nice.aceclean.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.TrafficStats
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val applicationInfo: ApplicationInfo,
    val sizeBytes: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val lastUsed: Long,
)

object DeviceStats {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            context.applicationInfo.uid,
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun launchableApps(context: Context): List<InstalledAppInfo> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolves = packageManager.queryIntentActivities(launcherIntent, 0)
        val end = System.currentTimeMillis()
        val start = end - 30L * 24 * 60 * 60 * 1000
        val usageByPackage = if (hasUsageAccess(context)) {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .associateBy { it.packageName }
        } else {
            emptyMap()
        }

        return resolves.asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { app ->
                InstalledAppInfo(
                    label = packageManager.getApplicationLabel(app).toString(),
                    packageName = app.packageName,
                    applicationInfo = app,
                    sizeBytes = runCatching { java.io.File(app.sourceDir).length() }.getOrDefault(0L),
                    rxBytes = TrafficStats.getUidRxBytes(app.uid).coerceAtLeast(0L),
                    txBytes = TrafficStats.getUidTxBytes(app.uid).coerceAtLeast(0L),
                    lastUsed = usageByPackage[app.packageName]?.lastTimeUsed ?: 0L,
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (value >= 100 || index == 0) "%.0f %s".format(value, units[index])
        else "%.1f %s".format(value, units[index])
    }

    fun formatLastUsed(timestamp: Long, neverText: String): String =
        if (timestamp <= 0) neverText else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}
