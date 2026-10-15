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

    override fun initOnboardingViews() {
        trackOnboardingAnimation(binding.scanAnimation)
        AdSdkManager.preloadAsync(this, AppAdManager.ONBOARDING_ANALYSIS)
        lifecycleScope.launch {
            animateProgress(SCAN_DURATION_MS, ::renderProgress)
            delay(SCAN_DURATION_MS)
            stopProgress()
            renderProgress(100)
            awaitResumed()
            navigateTo(BatteryInfoActivity::class.java)
        }
    }

    private fun renderProgress(progress: Int) {
        binding.progressView.text = getString(R.string.percentage_value, progress)
        binding.progressBar.progress = progress
    }

    private companion object { const val SCAN_DURATION_MS = 3_200L }
}
