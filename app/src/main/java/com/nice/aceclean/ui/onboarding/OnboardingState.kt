package com.nice.aceclean.ui.onboarding

import android.content.Context

internal enum class OnboardingStep {
    NOT_STARTED, OVERLAY, DEVICE_SCAN, DEVICE_DETAIL, BATTERY_SCAN, BATTERY_DETAIL,
    BATTERY_CHECK, JUNK_SCAN, JUNK_RESULT, CLEANING, COMPLETE, COMPLETED;

    fun restored(hotStart: Boolean): OnboardingStep = when (this) {
        // NOT_STARTED -> OVERLAY // 首次悬浮窗引导暂时停用。
        NOT_STARTED -> DEVICE_SCAN // Splash 结束后继续设备扫描，避免再次进入悬浮窗引导。
        OVERLAY -> DEVICE_SCAN
        DEVICE_DETAIL -> if (hotStart) this else DEVICE_SCAN
        JUNK_RESULT, CLEANING -> JUNK_SCAN // Scan results are never reconstructed from stale file paths.
        BATTERY_DETAIL -> BATTERY_SCAN
        else -> this
    }
}

internal object OnboardingState {
    private const val PREFS = "onboarding"
    private const val STEP = "step"

    fun get(context: Context): OnboardingStep {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(STEP, null)
        if (value != null) return runCatching { OnboardingStep.valueOf(value) }.getOrDefault(OnboardingStep.NOT_STARTED)
        // Existing users who finished the original guide should keep going directly to Home.
        return if (context.getSharedPreferences("first_run", Context.MODE_PRIVATE)
                .getBoolean("device_scan_complete", false)) OnboardingStep.COMPLETED else OnboardingStep.NOT_STARTED
    }

    fun set(context: Context, step: OnboardingStep) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(STEP, step.name).apply()
    }
}
