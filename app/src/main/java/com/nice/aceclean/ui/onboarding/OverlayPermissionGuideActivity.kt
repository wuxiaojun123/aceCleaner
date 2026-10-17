package com.nice.aceclean.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.OnboardingActivityOverlayPermissionGuideBinding
import com.nice.aceclean.ui.main.SplashActivity
import com.nice.aceclean.util.EventUtils

class OverlayPermissionGuideActivity : BaseOnboardingActivity<OnboardingActivityOverlayPermissionGuideBinding>(
    { OnboardingActivityOverlayPermissionGuideBinding.inflate(it) }, R.layout.onboarding_activity_overlay_permission_guide,
) {
    internal override val onboardingStep = OnboardingStep.OVERLAY
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val overlayPermission = OnboardingSettingsPermission(this, { Settings.canDrawOverlays(this) }) {
        continueToSplash()
    }

    override fun initOnboardingViews() {
        trackOnboardingAnimation(binding.guideAnimation)
        overlayPermission.restorePendingRequest()
        binding.skipButton.setTrackedClickListener(EventUtils.CLICK_OVERLAY_GUIDE_SKIP) { continueToSplash() }
        binding.allowButton.setTrackedClickListener(EventUtils.CLICK_OVERLAY_GUIDE_ALLOW) {
            val opened = overlayPermission.request(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            if (!opened) Toast.makeText(this, R.string.string_onboarding_overlay_permission_settings_unavailable, Toast.LENGTH_SHORT).show()
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun continueToSplash() {
        OnboardingState.set(this, OnboardingStep.DEVICE_SCAN)
        navigateTo(Intent(this, SplashActivity::class.java).putExtra(SplashActivity.EXTRA_FROM_ONBOARDING, true))
    }

    override fun onGuideBackPressed() = continueToSplash()
    override fun onDestroy() { overlayPermission.close(); super.onDestroy() }
}
