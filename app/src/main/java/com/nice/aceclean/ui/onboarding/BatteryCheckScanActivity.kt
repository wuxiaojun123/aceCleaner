package com.nice.aceclean.ui.onboarding

import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.databinding.OnboardingActivityBatteryCheckScanBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BatteryCheckScanActivity : BaseOnboardingActivity<OnboardingActivityBatteryCheckScanBinding>(
    { OnboardingActivityBatteryCheckScanBinding.inflate(it) }, R.layout.onboarding_activity_battery_check_scan,
) {
    internal override val onboardingStep = OnboardingStep.BATTERY_CHECK

    override fun initOnboardingViews() {
        trackOnboardingAnimation(binding.scanAnimation)
        lifecycleScope.launch {
            val ad = async { AppAdManager.preload(this@BatteryCheckScanActivity, AppAdManager.ONBOARDING_FINISH) }
            animateProgress(SCAN_DURATION_MS, ::renderProgress)
            delay(SCAN_DURATION_MS)
            stopProgress()
            renderProgress(100)
            if (ad.await()) showAd(AppAdManager.ONBOARDING_FINISH)
            awaitResumed()
            navigateTo(FirstJunkCompleteActivity.createIntent(this@BatteryCheckScanActivity, fromBattery = true))
        }
    }

    private fun renderProgress(progress: Int) {
        binding.progressView.text = getString(R.string.percentage_value, progress)
        binding.progressBar.progress = progress
    }

    private companion object { const val SCAN_DURATION_MS = 4_200L }
}
