package com.nice.aceclean.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.nice.aceclean.ui.main.MainActivity

/** Startup routing only. Page-specific transitions are declared in each Activity. */
internal object OnboardingNavigator {
    fun intent(context: Context, step: OnboardingStep): Intent = Intent(context, destination(step))

    fun destination(step: OnboardingStep): Class<out Activity> = when (step) {
        OnboardingStep.NOT_STARTED, OnboardingStep.OVERLAY -> OverlayPermissionGuideActivity::class.java
        OnboardingStep.DEVICE_SCAN -> DeviceStatusScanActivity::class.java
        OnboardingStep.DEVICE_DETAIL -> DeviceStatusDetailActivity::class.java
        OnboardingStep.BATTERY_SCAN -> BatteryInfoScanActivity::class.java
        OnboardingStep.BATTERY_DETAIL -> BatteryInfoActivity::class.java
        OnboardingStep.BATTERY_CHECK -> BatteryCheckScanActivity::class.java
        OnboardingStep.JUNK_SCAN -> FirstJunkStartScanningActivity::class.java
        OnboardingStep.JUNK_RESULT -> FirstJunkResultActivity::class.java
        OnboardingStep.CLEANING -> FirstJunkCleaningActivity::class.java
        OnboardingStep.COMPLETE -> FirstJunkCompleteActivity::class.java
        OnboardingStep.COMPLETED -> MainActivity::class.java
    }
}
