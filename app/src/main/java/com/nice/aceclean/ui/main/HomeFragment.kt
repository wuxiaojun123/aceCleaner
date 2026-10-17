package com.nice.aceclean.ui.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.FragmentHomeBinding
import com.nice.aceclean.permission.SpecialPermission
import com.nice.aceclean.permission.SpecialPermissionAccess
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.EventUtils
import com.nice.aceclean.util.NetworkSpeedSampler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment<FragmentHomeBinding>({ inflater, container, attach ->
    FragmentHomeBinding.inflate(inflater, container, attach)
}) {

    private var syncingNotificationSwitch = false

    override fun initViews() {
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
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
        }
        binding.notificationCard.setTrackedClickListener(EventUtils.CLICK_HOME_NOTIFICATION_TIP) {
            (activity as? MainActivity)?.openNotificationCleaner()
        }
        binding.notificationSwitch.setTrackedClickListener(EventUtils.CLICK_HOME_NOTIFICATION_SWITCH)
        binding.notificationSwitch.setOnCheckedChangeListener { _, _ ->
            if (!syncingNotificationSwitch) (activity as? MainActivity)?.openNotificationCleaner()
        }

        renderDeviceState()
        observeNetworkSpeed()
    }

    private fun setupPinnedTopBar() {
        binding.homeScrollView.setOnScrollChangeListener(
            androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                updatePinnedTopBar(scrollY)
            },
        )
        binding.homeScrollView.doOnLayout {
            updatePinnedTopBar(binding.homeScrollView.scrollY)
        }
    }

    private fun updatePinnedTopBar(scrollY: Int) {
        val showDistance = binding.homeHeader.bottom.coerceAtLeast(1)
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
        binding.storageUsage.text = getString(
            R.string.storage_usage_value,
            DeviceStats.formatBytes(requireContext(), storage.usedBytes),
            DeviceStats.formatBytes(requireContext(), storage.totalBytes),
        )
        val notificationPermissionGranted = SpecialPermissionAccess.isGranted(
            requireContext(),
            SpecialPermission.NOTIFICATION_LISTENER,
        )
        binding.notificationCard.visibility = if (notificationPermissionGranted) {
            View.GONE
        } else {
            View.VISIBLE
        }
        syncingNotificationSwitch = true
        binding.notificationSwitch.isChecked =
            notificationPermissionGranted
        syncingNotificationSwitch = false
    }

    private fun observeNetworkSpeed() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val speedSampler = NetworkSpeedSampler()
                while (true) {
                    delay(NETWORK_SAMPLE_INTERVAL_MS)
                    val bytesPerSecond = speedSampler.sampleBytesPerSecond()
                    binding.homePrimaryNetwork.homeNetworkSpeed.text =
                        getString(R.string.network_speed_value, NetworkSpeedSampler.format(bytesPerSecond))
                }
            }
        }
    }

    private companion object {
        const val NETWORK_SAMPLE_INTERVAL_MS = 1_000L
    }
}
