package com.nice.aceclean.ui.fragment

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.core.view.doOnLayout
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.FragmentHomeBinding
import com.nice.aceclean.databinding.ItemHomeFeatureCardBinding
import com.nice.aceclean.databinding.ViewHomeFeatureBadgeBinding
import com.nice.aceclean.permission.SpecialPermission
import com.nice.aceclean.permission.SpecialPermissionAccess
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.ui.main.MainActivity
import com.nice.aceclean.ui.main.ScanFeature
import com.nice.aceclean.ui.main.FeatureUsageStore
import com.nice.aceclean.ui.main.HomeFeatureBadgeState
import com.nice.aceclean.ui.main.homeFeatureBadgeState
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.EventUtils
import com.nice.aceclean.util.NetworkSpeedSampler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment<FragmentHomeBinding>({ inflater, container, attach ->
    FragmentHomeBinding.inflate(inflater, container, attach)
}) {

    private var syncingNotificationSwitch = false
    private var renderedDay: String? = null

    override fun initViews() {
        configureFeatureCards()
        setupPinnedTopBar()
        binding.cleanUpButton.setTrackedClickListener(EventUtils.CLICK_HOME_CLEANUP) {
            (activity as? MainActivity)?.openCleanUp()
        }
        binding.homePrimaryNetwork.root.setTrackedClickListener(EventUtils.CLICK_HOME_NETWORK) {
            (activity as? MainActivity)?.openNetworkTraffic()
        }
        binding.homeWideNotification.root.setTrackedClickListener(EventUtils.CLICK_HOME_NOTIFICATIONS) {
            (activity as? MainActivity)?.openNotificationCleaner()
        }
        binding.homePrimaryAppManager.root.setTrackedClickListener(EventUtils.CLICK_HOME_APPS) {
            (activity as? MainActivity)?.openAppManager()
        }
        binding.homeSecondaryScreenshot.root.setTrackedClickListener(EventUtils.CLICK_HOME_SCREENSHOTS) {
            (activity as? MainActivity)?.openScreenshotCleaner()
        }
        binding.homeSecondaryLargeFile.root.setTrackedClickListener(EventUtils.CLICK_HOME_LARGE_FILES) {
            (activity as? MainActivity)?.openBigFileCleaner()
        }
        binding.homeWideVideo.root.setTrackedClickListener(EventUtils.CLICK_HOME_VIDEOS) {
            (activity as? MainActivity)?.openVideoCleaner()
        }
        binding.homePrimaryDuplicate.root.setTrackedClickListener(EventUtils.CLICK_HOME_DUPLICATES) {
            (activity as? MainActivity)?.openDuplicatePhotoCleaner()
        }
        binding.homeToolRam.root.setTrackedClickListener(EventUtils.CLICK_HOME_RAM) {
            (activity as? MainActivity)?.openRamStatus()
        }
        binding.homeToolDevice.root.setTrackedClickListener(EventUtils.CLICK_HOME_DEVICE) {
            (activity as? MainActivity)?.openDeviceScan()
        }
        binding.homeToolBattery.root.setTrackedClickListener(EventUtils.CLICK_HOME_BATTERY) {
            (activity as? MainActivity)?.openBatteryInfo()
        }
        binding.homeToolNetworkTest.root.setTrackedClickListener(EventUtils.CLICK_HOME_SPEED_TEST) {
            (activity as? MainActivity)?.openNetworkTest()
        }

        binding.languageButton.setTrackedClickListener(EventUtils.CLICK_HOME_LANGUAGE) {
            (activity as? MainActivity)?.openLanguage()
        }
        binding.pinnedLanguageButton.setTrackedClickListener(EventUtils.CLICK_HOME_PINNED_LANGUAGE) {
            (activity as? MainActivity)?.openLanguage()
        }

        binding.settingsButton.setTrackedClickListener(EventUtils.CLICK_HOME_SETTINGS) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
        }
        binding.homeWideNotification.notificationSwitch.setTrackedClickListener(EventUtils.CLICK_HOME_NOTIFICATION_SWITCH)
        binding.homeWideNotification.notificationSwitch.setOnCheckedChangeListener { _, _ ->
            if (!syncingNotificationSwitch) (activity as? MainActivity)?.openNotificationCleaner()
        }

        renderDeviceState()
        observeNetworkSpeed()
    }

    private fun configureFeatureCards() {
        configureCard(binding.homePrimaryNetwork, R.string.network_traffic,
            R.string.network_speed_placeholder, R.drawable.ic_home_ref_network, "#087AF8", "#E4F0FF")
        configureCard(binding.homePrimaryDuplicate, R.string.dup_title,
            R.string.clean_success_duplicate_hint, R.drawable.ic_home_ref_duplicate, "#F59400", "#FFF0D9")
        configureCard(binding.homePrimaryAppManager, R.string.app_manager,
            R.string.installed_apps, R.drawable.ic_home_ref_apps, "#08B98D", "#DFF9F0")
        configureCard(binding.homeSecondaryScreenshot, R.string.screenshot_cleaner,
            R.string.clean_success_screenshot_hint, R.drawable.ic_home_ref_screenshots, "#7E58E9", "#F1EAFE")
        configureCard(binding.homeSecondaryLargeFile, R.string.home_large_files,
            R.string.clean_success_big_file_hint, R.drawable.ic_home_ref_large, "#F36659", "#FFE9E5")
        configureCard(binding.homeWideVideo, R.string.videos,
            R.string.string_delete_success_video_hint, R.drawable.ic_home_ref_videos, "#5669DA", "#E9ECFF")
        configureCard(binding.homeToolRam, R.string.ram_status,
            R.string.memory, R.drawable.ic_home_ref_ram, "#0BA7A9", "#DDF8F7")
        configureCard(binding.homeToolDevice, R.string.device_scan,
            R.string.string_onboarding_battery_check_scan_title, R.drawable.ic_home_ref_device, "#34A8E2", "#E1F5FF")
        configureCard(binding.homeToolBattery, R.string.battery_info,
            R.string.battery_health, R.drawable.ic_home_ref_battery, "#C69500", "#FFF8D9")
        configureCard(binding.homeToolNetworkTest, R.string.network_test,
            R.string.link_speed, R.drawable.ic_home_ref_speed, "#679C18", "#EFF8DE")
    }

    private fun configureCard(
        card: ItemHomeFeatureCardBinding,
        title: Int,
        subtitle: Int,
        icon: Int,
        foreground: String,
        background: String,
    ) {
        card.featureTitle.setText(title)
        card.featureSubtitle.setText(subtitle)
        card.featureIcon.setImageResource(icon)
        card.featureIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(foreground))
        card.featureIconBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor(background))
    }

    private fun setupPinnedTopBar() {
        binding.homeScrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                updatePinnedTopBar(scrollY)
            },
        )
        binding.homeScrollView.doOnLayout {
            updatePinnedTopBar(binding.homeScrollView.scrollY)
        }
    }

    private fun updatePinnedTopBar(scrollY: Int) {
        val showDistance = (binding.homeIntro.height * 0.4f).toInt().coerceAtLeast(1)
        val alpha = (scrollY.toFloat() / showDistance).coerceIn(0f, 1f)
        binding.homePinnedTopBar.alpha = alpha
        binding.homePinnedTopBar.visibility = if (alpha > 0f) View.VISIBLE else View.GONE
        // Keep the duplicate language entry out of accessibility traversal once the bar is shown.
        binding.homeHeader.importantForAccessibility = if (alpha >= 1f) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasViewBinding) renderDeviceState()
    }

    private fun renderDeviceState() {
        val storage = DeviceStats.storageSnapshot(requireContext())
        binding.storagePercentage.text =
            getString(R.string.percentage_value, storage.usedPercent)
        binding.storageGauge.usedPercent = storage.usedPercent
        binding.storageUsage.text = getString(
            R.string.storage_usage_value,
            DeviceStats.formatBytes(requireContext(), storage.usedBytes),
            DeviceStats.formatBytes(requireContext(), storage.totalBytes),
        )
        val notificationPermissionGranted = SpecialPermissionAccess.isGranted(
            requireContext(),
            SpecialPermission.NOTIFICATION_LISTENER,
        )
        syncingNotificationSwitch = true
        binding.homeWideNotification.notificationSwitch.isChecked =
            notificationPermissionGranted
        syncingNotificationSwitch = false
        renderFeatureBadges()
    }

    private fun renderFeatureBadges() {
        renderedDay = FeatureUsageStore.todayKey()
        renderFeatureBadge(binding.cleanUpButton, binding.cleanUpBadge, ScanFeature.CLEAN_UP)
        renderFeatureBadge(binding.homePrimaryDuplicate.root, binding.homePrimaryDuplicate.statusBadge, ScanFeature.DUPLICATE_PHOTO)
        renderFeatureBadge(binding.homeSecondaryScreenshot.root, binding.homeSecondaryScreenshot.statusBadge, ScanFeature.SCREENSHOT)
        renderFeatureBadge(binding.homeSecondaryLargeFile.root, binding.homeSecondaryLargeFile.statusBadge, ScanFeature.BIG_FILE)
        renderFeatureBadge(binding.homeWideVideo.root, binding.homeWideVideo.statusBadge, ScanFeature.VIDEO)
        renderFeatureBadge(binding.homeWideNotification.root, binding.homeWideNotification.statusBadge, ScanFeature.NOTIFICATIONS)
        // Keep daily reminders focused on cleaning; other tools only warn about missing permissions.
        renderFeatureBadge(binding.homePrimaryNetwork.root, binding.homePrimaryNetwork.statusBadge, ScanFeature.NETWORK_TRAFFIC, false)
        renderFeatureBadge(binding.homePrimaryAppManager.root, binding.homePrimaryAppManager.statusBadge, ScanFeature.APP_MANAGER, false)
        renderFeatureBadge(binding.homeToolRam.root, binding.homeToolRam.statusBadge, ScanFeature.RAM, false)
    }

    private fun renderFeatureBadge(
        entry: View,
        badge: ViewHomeFeatureBadgeBinding,
        feature: ScanFeature,
        dailyReminder: Boolean = true,
    ) {
        val state = homeFeatureBadgeState(
            feature.hasPermission(requireContext()),
            FeatureUsageStore.wasUsedToday(requireContext(), feature),
            dailyReminder,
        )
        badge.permissionWarning.visibility = if (state == HomeFeatureBadgeState.PERMISSION_REQUIRED) View.VISIBLE else View.GONE
        badge.unusedDot.visibility = if (state == HomeFeatureBadgeState.UNUSED_TODAY) View.VISIBLE else View.GONE
        ViewCompat.setStateDescription(entry, when (state) {
            HomeFeatureBadgeState.PERMISSION_REQUIRED -> getString(R.string.string_home_permission_required)
            HomeFeatureBadgeState.UNUSED_TODAY -> getString(R.string.string_home_unused_today)
            HomeFeatureBadgeState.NONE -> null
        })
    }

    private fun observeNetworkSpeed() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val speedSampler = NetworkSpeedSampler()
                while (true) {
                    delay(NETWORK_SAMPLE_INTERVAL_MS)
                    // Also refresh across local midnight while the home screen stays open.
                    if (renderedDay != FeatureUsageStore.todayKey()) renderFeatureBadges()
                    val bytesPerSecond = speedSampler.sampleBytesPerSecond()
                    binding.homePrimaryNetwork.featureSubtitle.text =
                        getString(R.string.network_speed_value, NetworkSpeedSampler.format(requireContext(), bytesPerSecond))
                }
            }
        }
    }

    private companion object {
        const val NETWORK_SAMPLE_INTERVAL_MS = 1_000L
    }
}
