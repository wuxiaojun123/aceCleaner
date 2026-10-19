package com.nice.aceclean.ui.onboarding

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.OnboardingActivityDeviceStatusDetailBinding
import com.nice.aceclean.databinding.OnboardingDialogFirstStorageAccessBinding
import com.nice.aceclean.permission.StorageAccess
import com.nice.aceclean.util.EventUtils
import com.nice.library_ad_sdk.AdSdkManager
import kotlinx.coroutines.launch

class DeviceStatusDetailActivity : BaseOnboardingActivity<OnboardingActivityDeviceStatusDetailBinding>(
    { OnboardingActivityDeviceStatusDetailBinding.inflate(it) }, R.layout.onboarding_activity_device_status_detail,
) {
    internal override val onboardingStep = OnboardingStep.DEVICE_DETAIL
    private val storageSettings = OnboardingSettingsPermission(this, { StorageAccess.hasBroadAccess(this) }, ::afterStoragePermission)
    private val legacyStorage = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        afterStoragePermission(StorageAccess.hasBroadAccess(this))
    }

    override fun initOnboardingViews() {
        storageSettings.restorePendingRequest()
        renderOnboardingDeviceInformation(binding)
        binding.skipButton.isVisible = true
        AdSdkManager.preloadAsync(this, AppAdManager.ONBOARDING_SKIP)
        binding.continueButton.setTrackedClickListener(EventUtils.CLICK_FIRST_DEVICE_CONTINUE) {
            if (StorageAccess.hasBroadAccess(this)) afterStoragePermission(true) else showStorageDialog()
        }
        binding.skipButton.setTrackedClickListener(EventUtils.CLICK_FIRST_DEVICE_SKIP) {
            binding.skipButton.isEnabled = false
            binding.continueButton.isEnabled = false
            lifecycleScope.launch { showAd(AppAdManager.ONBOARDING_SKIP); openHome() }
        }
        trackOnboardingAnimation(binding.continueButton)
    }

    private fun showStorageDialog() {
        val dialogBinding = OnboardingDialogFirstStorageAccessBinding.inflate(layoutInflater)
        showGuideDialog(dialogBinding)
        dialogBinding.closeButton.setTrackedClickListener(EventUtils.CLICK_FIRST_STORAGE_CLOSE) { dismissGuideDialog(); afterStoragePermission(false) }
        dialogBinding.allowButton.setTrackedClickListener(EventUtils.CLICK_FIRST_STORAGE_ALLOW) {
            dismissGuideDialog()
            if (Build.VERSION.SDK_INT >= 30) storageSettings.request(
                StorageAccess.broadAccessSettingsIntent(this), Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            ) else legacyStorage.launch(StorageAccess.legacyPermissions())
        }
    }

    private fun afterStoragePermission(granted: Boolean) {
        navigateTo(if (granted) FirstJunkStartScanningActivity::class.java else BatteryInfoScanActivity::class.java)
    }

    override fun onDestroy() {
        storageSettings.close()
        super.onDestroy()
    }
}
