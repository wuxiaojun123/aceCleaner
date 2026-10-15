package com.nice.aceclean.ui.onboarding

import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.databinding.OnboardingActivityFirstJunkStartScanningBinding
import com.nice.aceclean.ui.viewmodel.FirstJunkScanViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FirstJunkStartScanningActivity : BaseOnboardingActivity<OnboardingActivityFirstJunkStartScanningBinding>(
    { OnboardingActivityFirstJunkStartScanningBinding.inflate(it) }, R.layout.onboarding_activity_first_junk_start_scanning,
) {
    internal override val onboardingStep = OnboardingStep.JUNK_SCAN
    override val lightStatusBarIcons = true
    private val model: FirstJunkScanViewModel by viewModels()

    override fun initOnboardingViews() {
        lifecycleScope.launch {
            model.progress.collect { binding.scannedFiles.text = getString(R.string.items_found, it) }
        }
        lifecycleScope.launch {
            val ad = async { AppAdManager.preload(this@FirstJunkStartScanningActivity, AppAdManager.ONBOARDING_ANALYSIS) }
            val scan = model.scan()
            animateProgress(SCAN_DURATION_MS, ::renderProgress)
            delay(SCAN_DURATION_MS)
            FirstRunJunkSession.result = scan.await()
            stopProgress()
            renderProgress(100)
            delay(350L)
            if (ad.await()) showAd(AppAdManager.ONBOARDING_ANALYSIS)
            awaitResumed()
            navigateTo(FirstJunkResultActivity::class.java)
        }
    }

    private fun renderProgress(progress: Int) {
        binding.progressBar.progress = progress
        binding.progressView.text = getString(R.string.percentage_value, progress)
    }

    private companion object { const val SCAN_DURATION_MS = 4_600L }
}
