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
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.databinding.OnboardingActivityFirstJunkCompleteBinding
import com.nice.aceclean.databinding.OnboardingDialogFirstNotificationAccessBinding
import com.nice.aceclean.databinding.OnboardingDialogOverlayPermissionRequiredBinding
import com.nice.aceclean.permission.NotificationListenerPermissionHelper
import com.nice.aceclean.ui.main.BatteryInfoActivity as MainBatteryInfoActivity
import com.nice.aceclean.ui.main.NotificationCleanerActivity
import com.nice.aceclean.ui.main.RamStatusActivity
import com.nice.aceclean.ui.viewmodel.FirstJunkCompleteViewModel
import com.nice.library_ad_sdk.AdSdkManager
import kotlinx.coroutines.launch

class FirstJunkCompleteActivity : BaseOnboardingActivity<OnboardingActivityFirstJunkCompleteBinding>(
    { OnboardingActivityFirstJunkCompleteBinding.inflate(it) }, R.layout.onboarding_activity_first_junk_complete,
) {
    internal override val onboardingStep = OnboardingStep.COMPLETE
    private val model: FirstJunkCompleteViewModel by viewModels()
    private val notificationListener = NotificationListenerPermissionHelper(this) {
        dismissGuideDialog()
        openFeature(NotificationCleanerActivity::class.java)
    }
    private val overlayPermission = OnboardingSettingsPermission(this, { Settings.canDrawOverlays(this) }) { openHome() }

    override fun initOnboardingViews() {
        FirstRunJunkSession.result = null
        overlayPermission.restorePendingRequest()
        renderResult()
        binding.backButton.setOnClickListener { leaveComplete() }
        listOf(binding.appProcessCard, binding.appProcessButton).forEach { it.setOnClickListener { openFeature(RamStatusActivity::class.java) } }
        listOf(binding.notificationBlockerCard, binding.notificationBlockerButton).forEach { it.setOnClickListener { openFeature(NotificationCleanerActivity::class.java) } }
        listOf(binding.batteryInfoCard, binding.batteryInfoButton).forEach { it.setOnClickListener { openFeature(MainBatteryInfoActivity::class.java) } }
        lifecycleScope.launch {
            AdSdkManager.loadAndShowNative(this@FirstJunkCompleteActivity, AppAdManager.ONBOARDING_NATIVE, binding.adContainer, this@FirstJunkCompleteActivity)
        }
        if (!model.notificationPromptShown && !notificationListener.isGranted()) {
            model.notificationPromptShown = true
            binding.root.post { if (!isFinishing && !isDestroyed) showNotificationDialog() }
        }
    }

    private fun renderResult() {
        if (intent.getBooleanExtra(EXTRA_FROM_BATTERY, false)) {
            binding.titleView.setText(R.string.string_onboarding_battery_check_title)
            binding.resultMessage.setText(R.string.string_onboarding_first_junk_complete_battery_status_good)
            return
        }
        val size = Formatter.formatShortFileSize(this, intent.getLongExtra(EXTRA_CLEANED_BYTES, 0L))
        val message = getString(R.string.string_onboarding_first_junk_complete_description, size)
        binding.resultMessage.text = SpannableString(message).apply {
            val index = message.indexOf(size)
            if (index >= 0) {
                setSpan(StyleSpan(Typeface.BOLD), index, index + size.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(ContextCompat.getColor(this@FirstJunkCompleteActivity, R.color.color_FF3F73F5)), index, index + size.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun showNotificationDialog() {
        val dialogBinding = OnboardingDialogFirstNotificationAccessBinding.inflate(layoutInflater)
        showGuideDialog(dialogBinding)
        dialogBinding.closeButton.setOnClickListener { dismissGuideDialog() }
        dialogBinding.grantAccessButton.setOnClickListener { dismissGuideDialog(); notificationListener.request() }
    }

    private fun leaveComplete() {
        if (Settings.canDrawOverlays(this)) return openHome()
        val dialogBinding = OnboardingDialogOverlayPermissionRequiredBinding.inflate(layoutInflater)
        showGuideDialog(dialogBinding)
        dialogBinding.notNowButton.setOnClickListener { openHome() }
        dialogBinding.fixNowButton.setOnClickListener {
            dismissGuideDialog()
            val opened = overlayPermission.request(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            if (!opened) Toast.makeText(this, R.string.string_onboarding_overlay_permission_settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFeature(destination: Class<out Activity>) {
        OnboardingState.set(this, OnboardingStep.COMPLETED)
        navigateTo(destination)
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
