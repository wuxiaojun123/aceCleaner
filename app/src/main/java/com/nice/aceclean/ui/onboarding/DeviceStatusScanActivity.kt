package com.nice.aceclean.ui.onboarding

import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.databinding.OnboardingActivityDeviceStatusScanBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DeviceStatusScanActivity : BaseOnboardingActivity<OnboardingActivityDeviceStatusScanBinding>(
    { OnboardingActivityDeviceStatusScanBinding.inflate(it) }, R.layout.onboarding_activity_device_status_scan,
) {
    internal override val onboardingStep = OnboardingStep.DEVICE_SCAN
    override val lightStatusBarIcons = true

    override fun initOnboardingViews() {
        lifecycleScope.launch {
            val ad = async { AppAdManager.preload(this@DeviceStatusScanActivity, AppAdManager.ONBOARDING_DEVICE_SCAN) }
            animateProgress(SCAN_DURATION_MS, ::renderProgress)
            delay(SCAN_DURATION_MS)
            stopProgress()
            renderProgress(100)
            delay(450L)
            if (ad.await()) showAd(AppAdManager.ONBOARDING_DEVICE_SCAN)
            awaitResumed()
            navigateTo(DeviceStatusDetailActivity::class.java)
        }
    }

    private fun renderProgress(progress: Int) {
        binding.progressBar.progress = progress
        binding.progressView.text = getString(R.string.percentage_value, progress)
    }

    private companion object { const val SCAN_DURATION_MS = 4_200L }
}
