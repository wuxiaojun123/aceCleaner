package com.nice.aceclean.ui.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nice.aceclean.permission.SpecialPermission
import com.nice.aceclean.permission.SpecialPermissionAccess
import com.nice.aceclean.R
import com.nice.aceclean.databinding.FragmentHomeBinding
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.NetworkSpeedSampler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment<FragmentHomeBinding>({ inflater, container, attach ->
    FragmentHomeBinding.inflate(inflater, container, attach)
}) {

    private var syncingNotificationSwitch = false

    override fun initViews() {
        binding.cleanUpButton.setOnClickListener {
            (activity as? MainActivity)?.openCleanUp()
        }
        binding.homePrimaryNetwork.root.setOnClickListener {
            (activity as? MainActivity)?.openNetworkTraffic()
        }
        binding.homeWideNotification.root.setOnClickListener {
            (activity as? MainActivity)?.openNotificationCleaner()
        }
        binding.homePrimaryAppManager.root.setOnClickListener {
            (activity as? MainActivity)?.openAppManager()
        }
        binding.homeSecondaryScreenshot.root.setOnClickListener {
            (activity as? MainActivity)?.openScreenshotCleaner()
        }
        binding.homeSecondaryLargeFile.root.setOnClickListener {
            (activity as? MainActivity)?.openBigFileCleaner()
        }
        binding.homeWideVideo.root.setOnClickListener {
            (activity as? MainActivity)?.openVideoCleaner()
        }
        binding.homePrimaryDuplicate.root.setOnClickListener {
            (activity as? MainActivity)?.openDuplicatePhotoCleaner()
        }
        binding.homeToolRam.root.setOnClickListener {
            (activity as? MainActivity)?.openRamStatus()
        }
        binding.homeToolDevice.root.setOnClickListener {
            (activity as? MainActivity)?.openDeviceScan()
        }
        binding.homeToolBattery.root.setOnClickListener {
            (activity as? MainActivity)?.openBatteryInfo()
        }
        binding.homeToolNetworkTest.root.setOnClickListener {
            (activity as? MainActivity)?.openNetworkTest()
        }

        binding.languageButton.setOnClickListener {
            (activity as? MainActivity)?.openLanguage()
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
        }
        binding.notificationCard.setOnClickListener {
            (activity as? MainActivity)?.openNotificationCleaner()
        }
        binding.notificationSwitch.setOnCheckedChangeListener { _, _ ->
            if (!syncingNotificationSwitch) (activity as? MainActivity)?.openNotificationCleaner()
        }

        renderDeviceState()
        observeNetworkSpeed()
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
