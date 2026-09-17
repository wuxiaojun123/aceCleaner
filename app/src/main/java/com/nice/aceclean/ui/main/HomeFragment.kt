package com.nice.aceclean.ui.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.ui.widget.IosSwitchView
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.NetworkSpeedSampler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment(R.layout.fragment_home) {

    private lateinit var rootView: View
    private var syncingNotificationSwitch = false

    override fun initViews(root: View) {
        rootView = root
        root.findViewById<View>(R.id.clean_up_button).setOnClickListener {
            (activity as? MainActivity)?.openCleanUp()
        }
        root.findViewById<View>(R.id.network_traffic_card).setOnClickListener {
            (activity as? MainActivity)?.openNetworkTraffic()
        }
        root.findViewById<View>(R.id.notification_cleaner_card).setOnClickListener {
            (activity as? MainActivity)?.openNotificationCleaner()
        }
        root.findViewById<View>(R.id.app_manager_card).setOnClickListener {
            (activity as? MainActivity)?.openAppManager()
        }
        root.findViewById<View>(R.id.screenshot_cleaner_card).setOnClickListener {
            (activity as? MainActivity)?.openScreenshotCleaner()
        }
        root.findViewById<View>(R.id.big_file_cleaner_card).setOnClickListener {
            (activity as? MainActivity)?.openBigFileCleaner()
        }
        root.findViewById<View>(R.id.video_cleaner_card).setOnClickListener {
            (activity as? MainActivity)?.openVideoCleaner()
        }
        root.findViewById<View>(R.id.duplicate_photo_cleaner_card).setOnClickListener {
            (activity as? MainActivity)?.openDuplicatePhotoCleaner()
        }
        root.findViewById<View>(R.id.ram_status_card).setOnClickListener {
            (activity as? MainActivity)?.openRamStatus()
        }
        root.findViewById<View>(R.id.device_scan_card).setOnClickListener {
            (activity as? MainActivity)?.openDeviceScan()
        }
        root.findViewById<View>(R.id.battery_info_card).setOnClickListener {
            (activity as? MainActivity)?.openBatteryInfo()
        }
        root.findViewById<View>(R.id.network_test_card).setOnClickListener {
            (activity as? MainActivity)?.openNetworkTest()
        }

        root.findViewById<View>(R.id.language_button).setOnClickListener {
            (activity as? MainActivity)?.openLanguage()
        }

        root.findViewById<View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
        }
        root.findViewById<View>(R.id.notification_card).setOnClickListener {
            (activity as? MainActivity)?.openNotificationCleaner()
        }
        root.findViewById<IosSwitchView>(R.id.notification_switch).setOnCheckedChangeListener { _, _ ->
            if (!syncingNotificationSwitch) (activity as? MainActivity)?.openNotificationCleaner()
        }

        renderDeviceState()
        observeNetworkSpeed()
    }

    override fun onResume() {
        super.onResume()
        if (::rootView.isInitialized) renderDeviceState()
    }

    private fun renderDeviceState() {
        val storage = DeviceStats.storageSnapshot(requireContext())
        rootView.findViewById<TextView>(R.id.storage_percentage).text =
            getString(R.string.percentage_value, storage.usedPercent)
        rootView.findViewById<TextView>(R.id.storage_usage).text = getString(
            R.string.storage_usage_value,
            DeviceStats.formatBytes(requireContext(), storage.usedBytes),
            DeviceStats.formatBytes(requireContext(), storage.totalBytes),
        )
        syncingNotificationSwitch = true
        rootView.findViewById<IosSwitchView>(R.id.notification_switch).isChecked =
            NotificationManagerCompat.getEnabledListenerPackages(requireContext()).contains(requireContext().packageName)
        syncingNotificationSwitch = false
    }

    private fun observeNetworkSpeed() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val speedSampler = NetworkSpeedSampler()
                while (true) {
                    delay(NETWORK_SAMPLE_INTERVAL_MS)
                    val bytesPerSecond = speedSampler.sampleBytesPerSecond()
                    rootView.findViewById<TextView>(R.id.home_network_speed).text =
                        getString(R.string.network_speed_value, NetworkSpeedSampler.format(bytesPerSecond))
                }
            }
        }
    }

    private companion object {
        const val NETWORK_SAMPLE_INTERVAL_MS = 1_000L
    }
}
