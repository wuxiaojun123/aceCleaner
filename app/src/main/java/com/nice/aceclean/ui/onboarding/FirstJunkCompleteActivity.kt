package com.nice.aceclean.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.permission.PermissionRequestAnalytics
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.OnboardingActivityFirstJunkCompleteBinding
import com.nice.aceclean.databinding.OnboardingDialogFirstNotificationAccessBinding
import com.nice.aceclean.databinding.OnboardingDialogOverlayPermissionRequiredBinding
import com.nice.aceclean.permission.NotificationListenerPermissionHelper
import com.nice.aceclean.ui.main.BatteryInfoActivity as MainBatteryInfoActivity
import com.nice.aceclean.ui.main.NotificationCleanerActivity
import com.nice.aceclean.ui.main.RamStatusActivity
import com.nice.aceclean.ui.viewmodel.FirstJunkCompleteViewModel
import com.nice.aceclean.util.EventUtils
import com.nice.library_ad_sdk.AdSdkManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FirstJunkCompleteActivity : BaseOnboardingActivity<OnboardingActivityFirstJunkCompleteBinding>(
    { OnboardingActivityFirstJunkCompleteBinding.inflate(it) }, R.layout.onboarding_activity_first_junk_complete,
) {
    internal override val onboardingStep = OnboardingStep.COMPLETE
    private val model: FirstJunkCompleteViewModel by viewModels()
    private var notificationPrompt: Job? = null
    private val notificationListener = NotificationListenerPermissionHelper(this) {
        dismissGuideDialog()
        openFeature(NotificationCleanerActivity::class.java)
    }
    private val overlayPermission = OnboardingSettingsPermission(this, PermissionRequestAnalytics(this, PermissionRequestAnalytics.Type.OVERLAY), { Settings.canDrawOverlays(this) }) { openHome() }

    override fun initOnboardingViews() {
        FirstRunJunkSession.result = null
        overlayPermission.restorePendingRequest()
        trackOnboardingAnimation(binding.finishImage)
        renderResult()
        binding.backButton.setTrackedClickListener(EventUtils.CLICK_FIRST_COMPLETE_BACK) { leaveComplete() }
        listOf(binding.appProcessCard, binding.appProcessButton).forEach { it.setTrackedClickListener(EventUtils.CLICK_FIRST_COMPLETE_RAM) { openFeature(RamStatusActivity::class.java) } }
        listOf(binding.notificationBlockerCard, binding.notificationBlockerButton).forEach { it.setTrackedClickListener(EventUtils.CLICK_FIRST_COMPLETE_NOTIFICATIONS) { openFeature(NotificationCleanerActivity::class.java) } }
        listOf(binding.batteryInfoCard, binding.batteryInfoButton).forEach { it.setTrackedClickListener(EventUtils.CLICK_FIRST_COMPLETE_BATTERY) { openFeature(MainBatteryInfoActivity::class.java) } }
        lifecycleScope.launch {
            AdSdkManager.loadAndShowNative(this@FirstJunkCompleteActivity, AppAdManager.ONBOARDING_NATIVE, binding.adContainer, this@FirstJunkCompleteActivity)
        }
        if (!model.notificationPromptShown && !notificationListener.isGranted()) {
            notificationPrompt = lifecycleScope.launch {
                delay(1_600L) // Let the completion check settle before presenting the permission sheet.
                awaitResumed()
                if (!model.notificationPromptShown && !notificationListener.isGranted() && !isFinishing && !isDestroyed) {
                    model.notificationPromptShown = true
                    showNotificationDialog()
                }
            }
        }
    }

    private fun renderResult() {
        if (intent.getBooleanExtra(EXTRA_FROM_BATTERY, false)) {
            binding.titleView.setText(R.string.string_onboarding_battery_check_title)
            binding.completionStatusLabel.setText(R.string.string_onboarding_complete_check_status)
            binding.resultTitle.setText(R.string.string_onboarding_complete_battery_title)
            val message = getString(R.string.string_onboarding_complete_battery_result)
            binding.resultMessage.text = SpannableString(message).apply {
                val end = message.indexOf('\n').takeIf { it >= 0 } ?: message.length
                setSpan(RelativeSizeSpan(35f / 12f), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.BOLD), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(ContextCompat.getColor(this@FirstJunkCompleteActivity, R.color.color_FFAD431B)), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return
        }
        val size = Formatter.formatShortFileSize(this, intent.getLongExtra(EXTRA_CLEANED_BYTES, 0L))
        val message = getString(R.string.string_onboarding_complete_result, size)
        binding.resultMessage.text = SpannableString(message).apply {
            val index = message.indexOf(size)
            if (index >= 0) {
                val numberEnd = size.indexOfLast(Char::isDigit) + 1
                setSpan(RelativeSizeSpan(25f / 12f), index, index + size.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.BOLD), index, index + size.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(ContextCompat.getColor(this@FirstJunkCompleteActivity, R.color.color_FFAE4218)), index, index + size.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (numberEnd > 0) {
                    // The number also inherits the 25sp unit span; keep both responsive to font scale.
                    setSpan(RelativeSizeSpan(72f / 25f), index, index + numberEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(ContextCompat.getColor(this@FirstJunkCompleteActivity, R.color.color_FF252A30)), index, index + numberEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    private fun showNotificationDialog() {
        val dialogBinding = OnboardingDialogFirstNotificationAccessBinding.inflate(layoutInflater)
        dialogBinding.grantAccessButton.setShakeTarget(dialogBinding.actionContainer)
        showGuideDialog(dialogBinding)
        dialogBinding.closeButton.setTrackedClickListener(EventUtils.CLICK_FIRST_NOTIFICATION_CLOSE) { dismissGuideDialog() }
        dialogBinding.grantAccessButton.setTrackedClickListener(EventUtils.CLICK_FIRST_NOTIFICATION_ALLOW) { dismissGuideDialog(); notificationListener.request() }
    }

    private fun leaveComplete() {
        notificationPrompt?.cancel()
        if (Settings.canDrawOverlays(this)) return openHome()
        val dialogBinding = OnboardingDialogOverlayPermissionRequiredBinding.inflate(layoutInflater)
        dialogBinding.fixNowButton.setShakeTarget(dialogBinding.actionContainer)
        showGuideDialog(dialogBinding)
        dialogBinding.notNowButton.setTrackedClickListener(EventUtils.CLICK_FIRST_OVERLAY_LATER) { openHome() }
        dialogBinding.fixNowButton.setTrackedClickListener(EventUtils.CLICK_FIRST_OVERLAY_FIX) {
            dismissGuideDialog()
            val opened = overlayPermission.request(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            if (!opened) Toast.makeText(this, R.string.string_onboarding_overlay_permission_settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFeature(destination: Class<out Activity>) {
        notificationPrompt?.cancel()
        OnboardingState.set(this, OnboardingStep.COMPLETED)
        navigateToFeatureWithHome(destination)
    }

    override fun onGuideBackPressed() = leaveComplete()

    override fun onDestroy() {
        notificationListener.close()
        overlayPermission.close()
        AdSdkManager.destroy(AppAdManager.ONBOARDING_NATIVE)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_CLEANED_BYTES = "cleaned_bytes"
        private const val EXTRA_FROM_BATTERY = "from_battery"

        fun createIntent(context: Context, cleanedBytes: Long = 0L, fromBattery: Boolean = false): Intent =
            Intent(context, FirstJunkCompleteActivity::class.java)
                .putExtra(EXTRA_CLEANED_BYTES, cleanedBytes)
                .putExtra(EXTRA_FROM_BATTERY, fromBattery)
    }
}
