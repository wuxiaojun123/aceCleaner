package com.nice.aceclean.ui.main

import androidx.activity.viewModels
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.ActivityBatteryInfoBinding
import com.nice.aceclean.databinding.ItemBatteryInfoCardBinding
import com.nice.aceclean.util.BatterySnapshot
import com.nice.aceclean.util.DeviceToolsRepository
import com.nice.aceclean.util.EventUtils

class BatteryInfoActivity : DeviceToolActivity<ActivityBatteryInfoBinding>({ ActivityBatteryInfoBinding.inflate(it) }, R.layout.activity_battery_info, R.string.battery_info, EventUtils.CLICK_BATTERY_BACK) {

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

    override val featureHeader get() = binding.featureHeader

    private val handoff: FeatureResultViewModel by viewModels()

    override fun initToolViews() {
        val result = scanResult(handoff, ScanFeature.BATTERY) as? FeatureScanResult.Battery ?: return
        binding.batteryGotItButton.setTrackedClickListener(EventUtils.CLICK_BATTERY_GOT_IT) { finishWithBackInterstitial() }
        scanComplete = true
        renderBattery(result.value)
        binding.batteryScanBackground.cancelAnimation()
        binding.batteryScanAnimation.cancelAnimation()
        binding.batteryScanContainer.visibility = View.GONE
        binding.batteryResultContainer.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        if (scanComplete && !receiverRegistered) {
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
        binding.batteryCapacityValue.text = battery.estimatedCapacityMah
            ?.let { getString(R.string.capacity_value_spaced, it) }
            ?: getString(R.string.unknown)
        binding.batteryUsableTimeValue.text = battery.usableTimeMillis
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
        val card: ItemBatteryInfoCardBinding = when (cardId) {
            R.id.battery_health_card -> binding.batteryHealthCard
            R.id.battery_type_card -> binding.batteryTypeCard
            R.id.battery_status_card -> binding.batteryStatusCard
            R.id.battery_temperature_card -> binding.batteryTemperatureCard
            R.id.battery_voltage_card -> binding.batteryVoltageCard
            R.id.battery_current_card -> binding.batteryCurrentCard
            else -> error("Unknown battery card: $cardId")
        }
        card.batteryCardIcon.setImageResource(iconRes)
        card.batteryCardValue.text = value
        card.batteryCardLabel.setText(labelRes)
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

}
