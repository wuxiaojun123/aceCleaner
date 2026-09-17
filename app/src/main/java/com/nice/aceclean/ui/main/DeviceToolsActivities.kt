package com.nice.aceclean.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.DeviceToolsRepository
import com.nice.aceclean.util.BatterySnapshot
import com.nice.aceclean.util.NetworkSpeedTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class DeviceToolActivity(
    @param:LayoutRes layoutRes: Int,
    @param:StringRes private val titleRes: Int,
) : BaseActivity(layoutRes) {

    final override fun initViews() {
        view<TextView>(R.id.feature_title).setText(titleRes)
        view<android.view.View>(R.id.feature_back).setOnClickListener { finish() }
        initToolViews()
    }

    protected abstract fun initToolViews()

    protected fun addInfoRow(container: ViewGroup, @StringRes labelRes: Int, value: String) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_device_info_row, container, false)
        row.findViewById<TextView>(R.id.info_label).setText(labelRes)
        row.findViewById<TextView>(R.id.info_value).text = value
        container.addView(row)
    }
}

class RamStatusActivity : DeviceToolActivity(R.layout.activity_ram_status, R.string.ram_status) {

    override fun initToolViews() {
        view<View>(R.id.ram_ok_button).setOnClickListener { finish() }
    }

    override fun initData() {
        lifecycleScope.launch {
            val appsDeferred = async(Dispatchers.IO) {
                DeviceToolsRepository.runningApps(this@RamStatusActivity)
            }
            for (progress in 1..100) {
                view<TextView>(R.id.ram_scan_percent).text =
                    getString(R.string.percentage_value, progress)
                delay(SCAN_STEP_MILLIS)
            }
            val apps = appsDeferred.await()
            renderMemory()
            renderRunningApps(apps)
            view<com.airbnb.lottie.LottieAnimationView>(R.id.ram_scan_ring).cancelAnimation()
            view<com.airbnb.lottie.LottieAnimationView>(R.id.ram_scan_animation).cancelAnimation()
            view<View>(R.id.ram_scan_container).visibility = View.GONE
            view<View>(R.id.ram_result_container).visibility = View.VISIBLE
        }
    }

    private fun renderMemory() {
        val memory = DeviceToolsRepository.memorySnapshot(this)
        val availablePercent = (100 - memory.usedPercent).coerceIn(0, 100)
        view<TextView>(R.id.ram_percent).text = getString(R.string.percentage_value, availablePercent)
    }

    private fun renderRunningApps(apps: List<com.nice.aceclean.util.RunningAppInfo>) {
        view<TextView>(R.id.ram_running_summary).text = if (apps.isEmpty()) {
            getString(R.string.no_running_apps)
        } else {
            getString(R.string.running_apps_count, apps.size)
        }
        val container = view<ViewGroup>(R.id.ram_app_list)
        container.removeAllViews()
        apps.forEach { app ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_running_app, container, false)
            row.findViewById<android.widget.ImageView>(R.id.running_app_icon).setImageDrawable(
                packageManager.getApplicationIcon(app.applicationInfo),
            )
            row.findViewById<TextView>(R.id.running_app_name).text = app.label
            row.findViewById<android.view.View>(R.id.running_app_close).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(R.string.force_stop_title)
                    .setMessage(R.string.force_stop_explanation)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.continue_text) { _, _ ->
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${app.packageName}"),
                            ),
                        )
                    }
                    .show()
            }
            (row.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin =
                resources.getDimensionPixelSize(R.dimen.ram_app_row_spacing)
            container.addView(row)
        }
    }

    private companion object {
        const val SCAN_STEP_MILLIS = 150L
    }
}

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
        const val DEVICE_SCAN_STEP_MILLIS = 150L
    }
}

class BatteryInfoActivity : DeviceToolActivity(R.layout.activity_battery_info, R.string.battery_info) {

    private var receiverRegistered = false
    private var scanComplete = false
    private var latestBattery: BatterySnapshot? = null
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            latestBattery = DeviceToolsRepository.batterySnapshot(this@BatteryInfoActivity, intent)
            if (scanComplete) latestBattery?.let(::renderBattery)
        }
    }

    override fun initToolViews() {
        view<View>(R.id.battery_got_it_button).setOnClickListener { finish() }
    }

    override fun initData() {
        lifecycleScope.launch {
            for (progress in 1..100) {
                view<TextView>(R.id.battery_scan_percent).text =
                    getString(R.string.percentage_value, progress)
                delay(BATTERY_SCAN_STEP_MILLIS)
            }
            val battery = latestBattery ?: registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )?.let { DeviceToolsRepository.batterySnapshot(this@BatteryInfoActivity, it) }
            scanComplete = true
            battery?.let(::renderBattery)
            view<com.airbnb.lottie.LottieAnimationView>(R.id.battery_scan_background).cancelAnimation()
            view<com.airbnb.lottie.LottieAnimationView>(R.id.battery_scan_animation).cancelAnimation()
            view<View>(R.id.battery_scan_container).visibility = View.GONE
            view<View>(R.id.battery_result_container).visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun renderBattery(battery: BatterySnapshot) {
        val state = batteryState(battery.status)
        view<TextView>(R.id.battery_capacity_value).text = battery.estimatedCapacityMah
            ?.let { getString(R.string.capacity_value_spaced, it) }
            ?: getString(R.string.unknown)
        view<TextView>(R.id.battery_usable_time_value).text = battery.usableTimeMillis
            ?.let(::formatDuration)
            ?: getString(R.string.unknown)
        setBatteryCard(
            R.id.battery_health_card,
            R.drawable.batteryinfo_icon_health,
            batteryHealth(battery.health),
            R.string.battery_health,
        )
        setBatteryCard(
            R.id.battery_type_card,
            R.drawable.batteryinfo_icon_batterytype,
            battery.technology.ifBlank { getString(R.string.unknown) },
            R.string.battery_type,
        )
        setBatteryCard(
            R.id.battery_status_card,
            R.drawable.batteryinfo_icon_chargingstatus,
            state,
            R.string.charging_status,
        )
        setBatteryCard(
            R.id.battery_temperature_card,
            R.drawable.batteryinfo_icon_temperature,
            getString(R.string.temperature_value_compact, battery.temperatureCelsius),
            R.string.temperature,
        )
        setBatteryCard(
            R.id.battery_voltage_card,
            R.drawable.batteryinfo_icon_voltage,
            getString(R.string.voltage_value_compact, battery.voltageVolts),
            R.string.voltage,
        )
        setBatteryCard(
            R.id.battery_current_card,
            R.drawable.batteryinfo_icon_electric,
            battery.currentCapacityMah?.let { getString(R.string.current_capacity_value, it) }
                ?: getString(R.string.unknown),
            R.string.electric_current,
        )
    }

    private fun setBatteryCard(
        cardId: Int,
        @DrawableRes iconRes: Int,
        value: String,
        @StringRes labelRes: Int,
    ) {
        val card = view<View>(cardId)
        card.findViewById<ImageView>(R.id.battery_card_icon).setImageResource(iconRes)
        card.findViewById<TextView>(R.id.battery_card_value).text = value
        card.findViewById<TextView>(R.id.battery_card_label).setText(labelRes)
    }

    private fun batteryState(status: Int): String = getString(
        when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> R.string.charging
            BatteryManager.BATTERY_STATUS_DISCHARGING -> R.string.discharging
            BatteryManager.BATTERY_STATUS_FULL -> R.string.fully_charged
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.not_charging
            else -> R.string.unknown
        },
    )

    private fun batteryHealth(health: Int): String = getString(
        when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> R.string.battery_health_good
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.battery_health_overheat
            BatteryManager.BATTERY_HEALTH_DEAD -> R.string.battery_health_dead
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.battery_health_over_voltage
            BatteryManager.BATTERY_HEALTH_COLD -> R.string.battery_health_cold
            else -> R.string.unknown
        },
    )

    private fun formatDuration(durationMillis: Long): String {
        val totalMinutes = (durationMillis / 60_000L).coerceAtLeast(0L)
        return getString(R.string.duration_hours_minutes, totalMinutes / 60L, totalMinutes % 60L)
    }

    private companion object {
        const val BATTERY_SCAN_STEP_MILLIS = 300L
    }
}

class NetworkTestActivity : DeviceToolActivity(R.layout.activity_network_test, R.string.network_test) {

    private var testJob: Job? = null

    override fun initToolViews() {
        view<TextView>(R.id.network_type).text =
            getString(R.string.network_connection_value, connectionType())
        view<android.view.View>(R.id.network_test_button).setOnClickListener { startTest() }
    }

    override fun initData() {
        view<android.view.View>(R.id.network_test_button).post { startTest() }
    }

    override fun onDestroy() {
        testJob?.cancel()
        super.onDestroy()
    }

    private fun startTest() {
        if (testJob?.isActive == true) return
        if (!hasNetwork()) {
            view<TextView>(R.id.network_test_status).setText(R.string.network_unavailable)
            return
        }
        resetResults()
        testJob = lifecycleScope.launch {
            val button = view<TextView>(R.id.network_test_button)
            val status = view<TextView>(R.id.network_test_status)
            val progress = view<ProgressBar>(R.id.network_test_progress)
            button.isEnabled = false
            button.setText(R.string.testing)
            runCatching {
                status.setText(R.string.network_test_connecting)
                progress.progress = 12
                val ping = withContext(Dispatchers.IO) { NetworkSpeedTest.measurePing() }
                view<TextView>(R.id.network_ping).text = getString(R.string.ping_value, ping)

                status.setText(R.string.network_test_downloading)
                progress.progress = 38
                val download = withContext(Dispatchers.IO) { NetworkSpeedTest.measureDownloadMbps() }
                view<TextView>(R.id.network_download).text = getString(R.string.speed_value, download)

                status.setText(R.string.network_test_uploading)
                progress.progress = 72
                val upload = withContext(Dispatchers.IO) { NetworkSpeedTest.measureUploadMbps() }
                view<TextView>(R.id.network_upload).text = getString(R.string.speed_value, upload)

                progress.progress = 100
                status.setText(R.string.network_test_complete)
            }.onFailure {
                progress.progress = 0
                status.setText(R.string.network_test_failed)
            }
            button.isEnabled = true
            button.setText(R.string.test_again)
        }
    }

    private fun resetResults() {
        view<TextView>(R.id.network_ping).setText(R.string.ping_placeholder)
        view<TextView>(R.id.network_download).setText(R.string.speed_placeholder)
        view<TextView>(R.id.network_upload).setText(R.string.speed_placeholder)
        view<ProgressBar>(R.id.network_test_progress).progress = 0
        view<TextView>(R.id.network_type).text =
            getString(R.string.network_connection_value, connectionType())
    }

    private fun hasNetwork(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun connectionType(): String {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            ?: return getString(R.string.unknown)
        return getString(
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.string.connection_wifi
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> R.string.connection_mobile
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> R.string.connection_ethernet
                else -> R.string.connection_other
            },
        )
    }
}
