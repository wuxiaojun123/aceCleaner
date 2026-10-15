package com.nice.aceclean.ui.onboarding

import android.text.format.Formatter
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.databinding.OnboardingActivityFirstJunkCleaningBinding
import com.nice.aceclean.ui.viewmodel.FirstJunkCleaningViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FirstJunkCleaningActivity : BaseOnboardingActivity<OnboardingActivityFirstJunkCleaningBinding>(
    { OnboardingActivityFirstJunkCleaningBinding.inflate(it) }, R.layout.onboarding_activity_first_junk_cleaning,
) {
    internal override val onboardingStep = OnboardingStep.CLEANING
    override val lightStatusBarIcons = true
    private val model: FirstJunkCleaningViewModel by viewModels()

    override fun initOnboardingViews() {
        val result = FirstRunJunkSession.result ?: return navigateTo(FirstJunkStartScanningActivity::class.java)
        val selectedBytes = result.selectedBytes
        val progressJob = lifecycleScope.launch {
            model.progress.collect { binding.cleanedSize.text = Formatter.formatShortFileSize(this@FirstJunkCleaningActivity, selectedBytes * it / 100) }
        }
        lifecycleScope.launch {
            val ad = async { AppAdManager.preload(this@FirstJunkCleaningActivity, AppAdManager.ONBOARDING_FINISH) }
            val cleaning = model.clean(result)
            animateProgress(CLEAN_DURATION_MS, ::renderProgress)
            delay(CLEAN_DURATION_MS)
            val cleanedBytes = cleaning.await()
            progressJob.cancel()
            stopProgress()
            renderProgress(100)
            binding.cleanedSize.text = Formatter.formatShortFileSize(this@FirstJunkCleaningActivity, cleanedBytes)
            delay(350L)
            if (ad.await()) showAd(AppAdManager.ONBOARDING_FINISH)
            awaitResumed()
            navigateTo(FirstJunkCompleteActivity.createIntent(this@FirstJunkCleaningActivity, cleanedBytes))
        }
    }

    private fun renderProgress(progress: Int) {
        binding.progressBar.progress = progress
        binding.progressView.text = getString(R.string.percentage_value, progress)
    }

    private companion object { const val CLEAN_DURATION_MS = 3_200L }
}
