package com.nice.aceclean.ui.onboarding

import android.text.format.Formatter
import androidx.recyclerview.widget.LinearLayoutManager
import com.nice.aceclean.R
import com.nice.aceclean.databinding.OnboardingActivityFirstJunkResultBinding
import com.nice.aceclean.ui.adapter.OnboardingJunkAdapter

class FirstJunkResultActivity : BaseOnboardingActivity<OnboardingActivityFirstJunkResultBinding>(
    { OnboardingActivityFirstJunkResultBinding.inflate(it) }, R.layout.onboarding_activity_first_junk_result,
) {
    internal override val onboardingStep = OnboardingStep.JUNK_RESULT
    private var buttonAnimation: OnboardingButtonAnimation? = null

    override fun initOnboardingViews() {
        val result = FirstRunJunkSession.result ?: return navigateTo(FirstJunkStartScanningActivity::class.java)
        binding.categoryContainer.layoutManager = LinearLayoutManager(this)
        binding.categoryContainer.adapter = OnboardingJunkAdapter(result) { updateTotal(result) }
        updateTotal(result)
        binding.cleanButton.setOnClickListener {
            if (hasSelectedItems(result)) navigateTo(FirstJunkCleaningActivity::class.java)
            else navigateTo(FirstJunkCompleteActivity.createIntent(this, cleanedBytes = 0L))
        }
        buttonAnimation = OnboardingButtonAnimation(this, binding.cleanButton, binding.cleanButtonShine)
    }

    private fun hasSelectedItems(result: FirstRunJunkResult) = result.groups.any { group -> group.items.any { it.selected } }

    private fun updateTotal(result: FirstRunJunkResult) {
        binding.totalSize.text = Formatter.formatShortFileSize(this, result.selectedBytes)
        binding.cleanButtonText.setText(if (hasSelectedItems(result)) R.string.string_onboarding_first_junk_title else R.string.continue_text)
    }

    override fun onDestroy() { buttonAnimation?.close(); super.onDestroy() }
}
