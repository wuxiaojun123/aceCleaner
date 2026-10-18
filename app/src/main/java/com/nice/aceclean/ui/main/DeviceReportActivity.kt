package com.nice.aceclean.ui.main

import androidx.activity.viewModels
import android.view.View
import android.widget.GridLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.ActivityDeviceReportBinding
import com.nice.aceclean.databinding.ItemDeviceScanCardBinding
import com.nice.aceclean.util.DeviceToolsRepository
import com.nice.aceclean.util.EventUtils

class DeviceReportActivity : DeviceToolActivity<ActivityDeviceReportBinding>({ ActivityDeviceReportBinding.inflate(it) }, R.layout.activity_device_report, R.string.device_scan, EventUtils.CLICK_DEVICE_REPORT_BACK) {

    override val featureHeader get() = binding.featureHeader

    private val handoff: FeatureResultViewModel by viewModels()

    override fun initToolViews() {
        val result = scanResult(handoff, ScanFeature.DEVICE) as? FeatureScanResult.Device ?: return
        binding.deviceOkButton.setTrackedClickListener(EventUtils.CLICK_DEVICE_REPORT_OK) { finish() }
        renderReport(result.report)
        binding.deviceScanRing.cancelAnimation()
        binding.deviceScanAnimation.cancelAnimation()
        binding.deviceScanContainer.visibility = View.GONE
        binding.deviceResultContainer.visibility = View.VISIBLE
    }

    private fun renderReport(report: com.nice.aceclean.util.DeviceReport) {
        binding.deviceCpuValue.text =
            report.cpuModel.ifBlank { getString(R.string.unknown) }
        val grid = binding.deviceInfoGrid
        grid.removeAllViews()
        addDeviceCard(grid, R.drawable.device_scan_1, R.string.memory, report.memory)
        addDeviceCard(grid, R.drawable.device_scan_2, R.string.storage, report.storage)
        addDeviceCard(
            grid,
            R.drawable.device_scan_3,
            R.string.ram,
            getString(R.string.percentage_value, report.ramAvailablePercent),
        )
        addDeviceCard(
            grid,
            R.drawable.device_scan_4,
            R.string.android_version,
            getString(R.string.android_version_value, report.androidVersion),
        )
        addDeviceCard(
            grid,
            R.drawable.device_scan_5,
            R.string.resolution,
            getString(R.string.resolution_pixels_value, report.resolution),
        )
        addDeviceCard(
            grid,
            R.drawable.device_scan_6,
            R.string.opengl_es,
            getString(R.string.version_value, report.openGlEs),
        )
        addDeviceCard(
            grid,
            R.drawable.device_scan_7,
            R.string.vulkan,
            report.vulkanVersion?.let { getString(R.string.version_value, it) }
                ?: getString(R.string.not_supported),
        )
        addDeviceCard(grid, R.drawable.device_scan_8, R.string.ip_address, report.ipAddress)
        addDeviceCard(
            grid,
            R.drawable.device_scan_9,
            R.string.link_speed,
            report.linkSpeedMbps?.let { getString(R.string.link_speed_value, it) }
                ?: getString(R.string.unknown),
        )
    }

    private fun addDeviceCard(
        grid: GridLayout,
        @DrawableRes iconRes: Int,
        @StringRes labelRes: Int,
        value: String,
    ) {
        val card = ItemDeviceScanCardBinding.inflate(layoutInflater, grid, false)
        card.deviceCardIcon.setImageResource(iconRes)
        card.deviceCardValue.text = value
        card.deviceCardLabel.setText(labelRes)
        card.root.layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = resources.getDimensionPixelSize(R.dimen.device_card_height)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            val horizontal = resources.getDimensionPixelSize(R.dimen.device_card_horizontal_spacing)
            val bottom = resources.getDimensionPixelSize(R.dimen.device_card_vertical_spacing)
            setMargins(horizontal, 0, horizontal, bottom)
        }
        grid.addView(card.root)
    }

}
