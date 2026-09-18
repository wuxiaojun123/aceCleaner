package com.nice.aceclean.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.text.format.Formatter
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val applicationInfo: ApplicationInfo,
    val sizeBytes: Long,
    val installTime: Long,
    val installedOn: String,
    val rxBytes: Long,
    val txBytes: Long,
    val lastUsed: Long,
)

data class StorageSnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedPercent: Int
        get() = if (totalBytes <= 0L) 0 else ((usedBytes * 100.0) / totalBytes).toInt().coerceIn(0, 100)
}

object DeviceStats {

    fun storageSnapshot(context: Context): StorageSnapshot {
        val stats = StatFs(context.filesDir.absolutePath)
        return StorageSnapshot(stats.totalBytes, stats.availableBytes)
    }

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
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)

        return resolves.asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .distinctBy { it.packageName }
            .map { app ->
                val firstInstallTime = packageManager.installTime(app.packageName)

                InstalledAppInfo(
                    label = packageManager.getApplicationLabel(app).toString(),
                    packageName = app.packageName,
                    applicationInfo = app,
                    sizeBytes = installedSize(context, app),
                    installTime = firstInstallTime,
                    installedOn = firstInstallTime.takeIf { it > 0f }
                        ?.let { dateFormat.format(Date(it)) } ?: "",
                    rxBytes = TrafficStats.getUidRxBytes(app.uid).coerceAtLeast(0L),
                    txBytes = TrafficStats.getUidTxBytes(app.uid).coerceAtLeast(0L),
                    lastUsed = usageByPackage[app.packageName]?.lastTimeUsed ?: 0L,
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    private fun installedSize(context: Context, app: ApplicationInfo): Long {
        val storageStats = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) runCatching {
            val manager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            manager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                app.packageName,
                Process.myUserHandle(),
            ).let { it.appBytes + it.dataBytes + it.cacheBytes }
        }.getOrNull() else null
        if (storageStats != null && storageStats > 0L) return storageStats

        return buildList {
            add(app.sourceDir)
            app.splitSourceDirs?.let(::addAll)
        }.sumOf { path -> runCatching { java.io.File(path).length() }.getOrDefault(0L) }
    }

    @Suppress("DEPRECATION")
    private fun android.content.pm.PackageManager.installTime(packageName: String): Long =
        runCatching { getPackageInfo(packageName, 0).firstInstallTime }.getOrDefault(0L)

    fun formatBytes(context: Context, bytes: Long): String =
        Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0L))

    fun formatLastUsed(timestamp: Long, neverText: String): String =
        if (timestamp <= 0) neverText else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}
