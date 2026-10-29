package com.nice.aceclean.ui.main

import android.view.View
import androidx.activity.viewModels
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.ActivityDeviceReportBinding
import com.nice.aceclean.util.DeviceReport
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.DeviceToolsRepository
import com.nice.aceclean.util.EventUtils

class DeviceReportActivity : DeviceToolActivity<ActivityDeviceReportBinding>(
    { ActivityDeviceReportBinding.inflate(it) },
    R.layout.activity_device_report,
    R.string.device_scan,
    EventUtils.CLICK_DEVICE_REPORT_BACK,
) {
    override val featureHeader get() = binding.featureHeader
    private val handoff: FeatureResultViewModel by viewModels()

    override fun initToolViews() {
        val result = scanResult(handoff, ScanFeature.DEVICE) as? FeatureScanResult.Device ?: return
        binding.deviceOkButton.setTrackedClickListener(EventUtils.CLICK_DEVICE_REPORT_OK) { finishWithBackInterstitial() }
        renderReport(result.report)
        binding.deviceScanRing.cancelAnimation()
        binding.deviceScanAnimation.cancelAnimation()
        binding.deviceScanContainer.visibility = View.GONE
        binding.deviceResultContainer.visibility = View.VISIBLE
    }

    private fun renderReport(report: DeviceReport) = with(binding) {
        val memory = DeviceToolsRepository.memorySnapshot(this@DeviceReportActivity)
        val storage = DeviceStats.storageSnapshot(this@DeviceReportActivity)
        deviceProcessorValue.text = getString(R.string.string_device_core_count, Runtime.getRuntime().availableProcessors())
        deviceMemoryValue.text = DeviceStats.formatBytes(this@DeviceReportActivity, memory.totalBytes)
        deviceStorageValue.text = DeviceStats.formatBytes(this@DeviceReportActivity, storage.totalBytes)
        deviceAndroidValue.text = getString(R.string.android_version_value, report.androidVersion)
        deviceRamValue.text = getString(R.string.percentage_value, report.ramAvailablePercent)
        deviceCpuValue.text = report.cpuModel.ifBlank { getString(R.string.unknown) }
        deviceResolutionValue.text = getString(R.string.resolution_pixels_value, report.resolution)
        deviceOpenglValue.text = report.openGlEs
        deviceVulkanValue.text = report.vulkanVersion ?: getString(R.string.not_supported)
        deviceLinkValue.text = report.linkSpeedMbps?.let { getString(R.string.link_speed_value, it) }
            ?: getString(R.string.unknown)
        deviceIpValue.text = report.ipAddress
        deviceConnected.visibility = if (report.linkSpeedMbps != null) View.VISIBLE else View.GONE
    }
}
