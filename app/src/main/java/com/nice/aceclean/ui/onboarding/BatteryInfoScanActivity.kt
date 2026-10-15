package com.nice.aceclean.ui.onboarding

import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.databinding.OnboardingActivityBatteryInfoScanBinding
import com.nice.library_ad_sdk.AdSdkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BatteryInfoScanActivity : BaseOnboardingActivity<OnboardingActivityBatteryInfoScanBinding>(
    { OnboardingActivityBatteryInfoScanBinding.inflate(it) }, R.layout.onboarding_activity_battery_info_scan,
) {
    internal override val onboardingStep = OnboardingStep.BATTERY_SCAN
    override val lightStatusBarIcons = true

    override fun initOnboardingViews() {
        AdSdkManager.preloadAsync(this, AppAdManager.ONBOARDING_ANALYSIS)
        lifecycleScope.launch {
            animateProgress(SCAN_DURATION_MS) { binding.progressView.text = getString(R.string.percentage_value, it) }
            delay(SCAN_DURATION_MS)
            stopProgress()
            binding.progressView.text = getString(R.string.percentage_value, 100)
            awaitResumed()
            navigateTo(BatteryInfoActivity::class.java)
        }
    }

    private companion object { const val SCAN_DURATION_MS = 3_200L }
}
