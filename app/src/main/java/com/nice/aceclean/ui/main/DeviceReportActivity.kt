package com.nice.aceclean.ui.main

import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.util.DeviceToolsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DeviceReportActivity : DeviceToolActivity(R.layout.activity_device_report, R.string.device_scan) {

    override fun initToolViews() {
        view<View>(R.id.device_ok_button).setOnClickListener { finish() }
    }

    override fun initData() {
        lifecycleScope.launch {
            val reportDeferred = async(Dispatchers.Default) {
                DeviceToolsRepository.deviceReport(this@DeviceReportActivity)
            }
            for (value in 1..100) {
                view<TextView>(R.id.device_scan_percent).text =
                    getString(R.string.percentage_value, value)
                delay(DEVICE_SCAN_STEP_MILLIS)
            }
            val report = reportDeferred.await()
            renderReport(report)
            view<com.airbnb.lottie.LottieAnimationView>(R.id.device_scan_ring).cancelAnimation()
            view<com.airbnb.lottie.LottieAnimationView>(R.id.device_scan_animation).cancelAnimation()
            view<View>(R.id.device_scan_container).visibility = View.GONE
            view<View>(R.id.device_result_container).visibility = View.VISIBLE
        }
    }

    private fun renderReport(report: com.nice.aceclean.util.DeviceReport) {
        view<TextView>(R.id.device_cpu_value).text =
            report.cpuModel.ifBlank { getString(R.string.unknown) }
        val grid = view<GridLayout>(R.id.device_info_grid)
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
        val card = LayoutInflater.from(this).inflate(R.layout.item_device_scan_card, grid, false)
        card.findViewById<ImageView>(R.id.device_card_icon).setImageResource(iconRes)
        card.findViewById<TextView>(R.id.device_card_value).text = value
        card.findViewById<TextView>(R.id.device_card_label).setText(labelRes)
        card.layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = resources.getDimensionPixelSize(R.dimen.device_card_height)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            val horizontal = resources.getDimensionPixelSize(R.dimen.device_card_horizontal_spacing)
            val bottom = resources.getDimensionPixelSize(R.dimen.device_card_vertical_spacing)
            setMargins(horizontal, 0, horizontal, bottom)
        }
        grid.addView(card)
    }

    private companion object {
        const val DEVICE_SCAN_STEP_MILLIS = 30L
    }
}
