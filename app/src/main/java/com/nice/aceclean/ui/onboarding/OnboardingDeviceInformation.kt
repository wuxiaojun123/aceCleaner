package com.nice.aceclean.ui.onboarding

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TextAppearanceSpan
import android.text.format.Formatter
import android.graphics.Typeface
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.nice.aceclean.R
import java.io.File
import java.util.Locale
internal fun android.app.Activity.renderOnboardingDeviceInformation(binding: com.nice.aceclean.databinding.OnboardingActivityDeviceStatusDetailBinding) {
    binding.deviceName.text = Build.MANUFACTURER.uppercase(Locale.getDefault())
    binding.deviceModel.text = Build.MODEL.replaceFirst(Regex("\\s+"), "\n")
    binding.androidVersion.text = getString(R.string.guide_android_version, Build.VERSION.RELEASE)
    binding.androidRelease.text = Build.VERSION.CODENAME.uppercase(Locale.getDefault())

    val memory = (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).let { manager ->
        ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    }
    val usedRam = (memory.totalMem - memory.availMem).coerceAtLeast(0L)
    val ramPercent = ((usedRam * 100L) / memory.totalMem.coerceAtLeast(1L)).toInt()
    binding.ramPercent.text = styleDeviceDetailUnit(
        getString(R.string.percentage_value, ramPercent), 20f / 43f, R.color.color_FF707985,
    )
    binding.ramProgress.progress = ramPercent
    binding.ramValue.text = Formatter.formatShortFileSize(this, usedRam)
    binding.ramTotalValue.text = Formatter.formatShortFileSize(this, memory.totalMem)

    val stat = StatFs(Environment.getDataDirectory().path)
    val usedStorage = (stat.totalBytes - stat.availableBytes).coerceAtLeast(0L)
    val storagePercent = ((usedStorage * 100L) / stat.totalBytes.coerceAtLeast(1L)).toInt()
    binding.storagePercent.text = styleDeviceDetailUnit(
        getString(R.string.percentage_value, storagePercent), 20f / 43f, R.color.color_FF707985,
    )
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
    binding.batteryPercent.text = styleDeviceDetailUnit(
        getString(R.string.percentage_value, percent), 15f / 27f, R.color.color_FF252A30,
    )
    binding.batteryProgress.progress = percent
    binding.batteryValue.text = statusText

    val cpuModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
    val frequency = runCatching {
        File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000
    }.getOrNull()?.let { getString(R.string.string_onboarding_frequency, it) } ?: getString(R.string.string_onboarding_literal_2)
    binding.cpuModelValue.text = cpuModel.ifBlank { Build.SUPPORTED_ABIS.firstOrNull().orEmpty() }
    binding.cpuFrequencyValue.text = styleDeviceDetailUnit(frequency, 10f / 24f)
    binding.cpuCoresValue.text = Runtime.getRuntime().availableProcessors().toString()

    val metrics = resources.displayMetrics
    @Suppress("DEPRECATION") val refreshRate = windowManager.defaultDisplay.refreshRate
    binding.screenResolutionValue.text = getString(R.string.guide_screen_resolution_value, metrics.widthPixels, metrics.heightPixels)
    binding.screenDpiValue.text = styleDeviceDetailUnit(getString(R.string.string_onboarding_dpi, metrics.densityDpi), 10f / 24f)
    binding.screenRefreshValue.text = styleDeviceDetailUnit(getString(R.string.string_onboarding_refresh_rate, refreshRate.toInt()), 10f / 24f)
}

/** Keeps localized values intact; only the unit's size, weight and color change. */
private fun Context.styleDeviceDetailUnit(
    value: String,
    relativeSize: Float,
    @ColorRes colorRes: Int = R.color.color_FF616975,
): CharSequence {
    val firstDigit = value.indexOfFirst(Char::isDigit)
    val lastDigit = value.indexOfLast(Char::isDigit)
    if (firstDigit < 0) return value
    return SpannableString(value).apply {
        fun styleRange(start: Int, end: Int) {
            if (start >= end) return
            setSpan(TextAppearanceSpan("sans-serif", Typeface.NORMAL, -1, null, null), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(relativeSize), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(ContextCompat.getColor(this@styleDeviceDetailUnit, colorRes)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        styleRange(0, firstDigit)
        styleRange(lastDigit + 1, value.length)
    }
}

