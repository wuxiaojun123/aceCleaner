package com.nice.aceclean.ui.onboarding

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import com.nice.aceclean.R
import java.io.File
import java.util.Locale
internal fun android.app.Activity.renderOnboardingDeviceInformation(binding: com.nice.aceclean.databinding.OnboardingActivityDeviceStatusDetailBinding) {
    binding.deviceName.text = Build.MANUFACTURER.uppercase(Locale.getDefault())
    binding.deviceModel.text = Build.MODEL
    binding.androidVersion.text = getString(R.string.guide_android_version, Build.VERSION.RELEASE)
    binding.androidRelease.text = Build.VERSION.CODENAME.uppercase(Locale.getDefault())

    val memory = (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).let { manager ->
        ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    }
    val usedRam = (memory.totalMem - memory.availMem).coerceAtLeast(0L)
    val ramPercent = ((usedRam * 100L) / memory.totalMem.coerceAtLeast(1L)).toInt()
    binding.ramPercent.text = getString(R.string.percentage_value, ramPercent)
    binding.ramProgress.progress = ramPercent
    binding.ramValue.text = Formatter.formatShortFileSize(this, usedRam)
    binding.ramTotalValue.text = Formatter.formatShortFileSize(this, memory.totalMem)

    val stat = StatFs(Environment.getDataDirectory().path)
    val usedStorage = (stat.totalBytes - stat.availableBytes).coerceAtLeast(0L)
    val storagePercent = ((usedStorage * 100L) / stat.totalBytes.coerceAtLeast(1L)).toInt()
    binding.storagePercent.text = getString(R.string.percentage_value, storagePercent)
    binding.storageProgress.progress = storagePercent
    binding.storageValue.text = Formatter.formatShortFileSize(this, usedStorage)
    binding.storageTotalValue.text = Formatter.formatShortFileSize(this, stat.totalBytes)

    val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val percent = if (level >= 0) level * 100 / scale.coerceAtLeast(1) else 0
    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val statusText = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> getString(R.string.charging)
        BatteryManager.BATTERY_STATUS_FULL -> getString(R.string.string_onboarding_battery_home_full)
        else -> getString(R.string.not_charging)
    }
    binding.batteryPercent.text = getString(R.string.percentage_value, percent)
    binding.batteryProgress.progress = percent
    binding.batteryValue.text = statusText

    val cpuModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
    val frequency = runCatching {
        File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000
    }.getOrNull()?.let { getString(R.string.string_onboarding_frequency, it) } ?: getString(R.string.string_onboarding_literal_2)
    binding.cpuModelValue.text = cpuModel.ifBlank { Build.SUPPORTED_ABIS.firstOrNull().orEmpty() }
    binding.cpuFrequencyValue.text = frequency
    binding.cpuCoresValue.text = Runtime.getRuntime().availableProcessors().toString()

    val metrics = resources.displayMetrics
    @Suppress("DEPRECATION") val refreshRate = windowManager.defaultDisplay.refreshRate
    binding.screenResolutionValue.text = getString(R.string.guide_screen_resolution_value, metrics.widthPixels, metrics.heightPixels)
    binding.screenDpiValue.text = getString(R.string.string_onboarding_dpi, metrics.densityDpi)
    binding.screenRefreshValue.text = getString(R.string.string_onboarding_refresh_rate, refreshRate.toInt())
}

