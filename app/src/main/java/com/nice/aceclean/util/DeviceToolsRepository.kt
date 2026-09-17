package com.nice.aceclean.util

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.net.Inet4Address
import java.util.Locale
import kotlin.math.roundToLong

data class MemorySnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedPercent: Int
        get() = calculatePercent(usedBytes, totalBytes)
}

data class DeviceReport(
    val androidVersion: String,
    val cpuModel: String,
    val memory: String,
    val storage: String,
    val ramAvailablePercent: Int,
    val resolution: String,
    val ipAddress: String,
    val openGlEs: String,
    val vulkanVersion: String?,
    val linkSpeedMbps: Int?,
)

data class BatterySnapshot(
    val levelPercent: Int,
    val status: Int,
    val health: Int,
    val technology: String,
    val temperatureCelsius: Double,
    val voltageVolts: Double,
    val estimatedCapacityMah: Long?,
    val currentCapacityMah: Long?,
    val usableTimeMillis: Long?,
)

data class RunningAppInfo(
    val label: String,
    val packageName: String,
    val applicationInfo: ApplicationInfo,
)

object DeviceToolsRepository {

    fun memorySnapshot(context: Context): MemorySnapshot {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return MemorySnapshot(info.totalMem, info.availMem)
    }

    fun runningApps(context: Context): List<RunningAppInfo> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processPackages = activityManager.runningAppProcesses.orEmpty()
            .flatMap { process -> process.pkgList?.asList().orEmpty() }
        val now = System.currentTimeMillis()
        val start = now - RECENT_APP_WINDOW_MILLIS
        val recentlyUsedPackages = if (DeviceStats.hasUsageAccess(context)) {
            val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                .asSequence()
                .filter { it.lastTimeUsed >= start }
                .sortedByDescending { it.lastTimeUsed }
                .map { it.packageName }
                .toList()
        } else {
            emptyList()
        }
        val packages = LinkedHashSet<String>().apply {
            addAll(processPackages)
            addAll(recentlyUsedPackages)
        }
        val packageManager = context.packageManager
        return packages.mapNotNull { packageName ->
            runCatching {
                if (packageName == context.packageName) return@runCatching null
                val app = packageManager.getApplicationInfo(packageName, 0)
                val isSystemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val hasLauncher = packageManager.getLaunchIntentForPackage(packageName) != null
                if (isSystemApp && !hasLauncher && packageName !in processPackages &&
                    !packageName.startsWith("io.appium.")) {
                    return@runCatching null
                }
                RunningAppInfo(
                    label = packageManager.getApplicationLabel(app).toString(),
                    packageName = packageName,
                    applicationInfo = app,
                )
            }.getOrNull()
        }.distinctBy { it.packageName }
    }

    fun deviceReport(context: Context): DeviceReport {
        val memory = memorySnapshot(context)
        val storage = DeviceStats.storageSnapshot(context)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metrics)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val glVersion = activityManager.deviceConfigurationInfo.glEsVersion ?: "-"
        return DeviceReport(
            androidVersion = Build.VERSION.RELEASE,
            cpuModel = cpuModel(),
            memory = "${formatReportBytes(memory.availableBytes)} / ${formatReportBytes(memory.totalBytes)}",
            storage = "${formatReportBytes(storage.availableBytes, forceGigabytes = true)} / " +
                formatReportBytes(storage.totalBytes, forceGigabytes = true),
            ramAvailablePercent = calculatePercent(memory.availableBytes, memory.totalBytes),
            resolution = "${metrics.widthPixels} x ${metrics.heightPixels}",
            ipAddress = ipAddress(context),
            openGlEs = glVersion,
            vulkanVersion = vulkanVersion(context),
            linkSpeedMbps = linkSpeedMbps(context),
        )
    }

    fun batterySnapshot(context: Context, intent: Intent): BatterySnapshot {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val levelPercent = calculatePercent(level.toLong(), scale.toLong())
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val chargeMicroAmpHours = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeUnless { it == Int.MIN_VALUE || it <= 0 }
        val chargeCounterCapacity = if (chargeMicroAmpHours != null && levelPercent > 0) {
            (chargeMicroAmpHours.toLong() * 100L / levelPercent / 1_000L).takeIf { it in 100L..20_000L }
        } else {
            null
        }
        val estimatedCapacity = nominalBatteryCapacityMah(context) ?: chargeCounterCapacity
        val currentCapacity = estimatedCapacity
            ?.times(levelPercent)
            ?.div(100L)
        // Keep the same estimate used by the reference app: current capacity / 300 mA.
        val usableTime = currentCapacity
            ?.times(60L * 60L * 1_000L)
            ?.div(300L)
        return BatterySnapshot(
            levelPercent = levelPercent,
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
            health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN),
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty(),
            temperatureCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0,
            voltageVolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1_000.0,
            estimatedCapacityMah = estimatedCapacity,
            currentCapacityMah = currentCapacity,
            usableTimeMillis = usableTime,
        )
    }

    private fun nominalBatteryCapacityMah(context: Context): Long? = runCatching {
        val profileClass = Class.forName("com.android.internal.os.PowerProfile")
        val profile = profileClass.getConstructor(Context::class.java).newInstance(context)
        val value = profileClass.getMethod("getBatteryCapacity").invoke(profile) as Number
        value.toDouble().roundToLong()
    }.getOrNull()?.takeIf { it in 100L..20_000L }

    private fun cpuModel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL.isNotBlank()) {
            return Build.SOC_MODEL
        }
        val rawModel = runCatching {
            java.io.File("/proc/cpuinfo").useLines { lines ->
                lines.firstOrNull { line ->
                    line.startsWith("Hardware", true) ||
                        line.startsWith("model name", true) ||
                        line.startsWith("Processor", true)
                }?.substringAfter(':')?.trim()
            }
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: Build.HARDWARE
        return if (rawModel.startsWith("AArch64 Processor", ignoreCase = true)) {
            rawModel.replaceFirst("AArch64", "ARMv8", ignoreCase = true)
                .replace("(aarch64)", "(v8l)", ignoreCase = true)
        } else {
            rawModel
        }
    }

    private fun ipAddress(context: Context): String {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return "-"
        return connectivity.getLinkProperties(network)?.linkAddresses
            ?.asSequence()
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
            ?: "-"
    }

    @Suppress("DEPRECATION")
    private fun linkSpeedMbps(context: Context): Int? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.connectionInfo?.linkSpeed?.takeIf { it > 0 }?.let { return it }
        }
        return capabilities.linkDownstreamBandwidthKbps
            .takeIf { it > 0 }
            ?.div(1_000)
            ?.takeIf { it > 0 }
    }

    private fun formatReportBytes(bytes: Long, forceGigabytes: Boolean = false): String {
        val gigabyte = 1_000_000_000.0
        val megabyte = 1_000_000.0
        return if (forceGigabytes || bytes >= gigabyte) {
            String.format(Locale.US, "%.2f GB", bytes / gigabyte)
        } else {
            String.format(Locale.US, "%.0f MB", bytes / megabyte)
        }
    }

    private fun vulkanVersion(context: Context): String? {
        val feature = context.packageManager.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            ?: return null
        if (feature.version <= 0) return null
        val major = feature.version ushr 22
        val minor = (feature.version ushr 12) and 0x3ff
        val patch = feature.version and 0xfff
        return if (patch == 0) "$major.$minor" else "$major.$minor.$patch"
    }

    private const val RECENT_APP_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1_000L
}

internal fun calculatePercent(value: Long, total: Long): Int =
    if (total <= 0L) 0 else ((value.toDouble() * 100.0) / total).toInt().coerceIn(0, 100)
