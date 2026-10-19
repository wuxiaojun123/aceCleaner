package com.nice.aceclean.ui.onboarding

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.OnboardingActivityBatteryInfoBinding
import com.nice.aceclean.util.EventUtils
import kotlinx.coroutines.launch

/** Battery detail used by the first-run flow; the main feature keeps its own Activity. */
class FirstBatteryInfoActivity : BaseOnboardingActivity<OnboardingActivityBatteryInfoBinding>(
    { OnboardingActivityBatteryInfoBinding.inflate(it) }, R.layout.onboarding_activity_battery_info,
) {
    internal override val onboardingStep = OnboardingStep.BATTERY_DETAIL
    private var receiverRegistered = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = renderBatteryInformation(intent)
    }

    override fun initOnboardingViews() {
        binding.backButton.isVisible = false
        binding.bottomActionContainer.isVisible = true
        trackOnboardingAnimation(binding.scanAnimation)
        renderBatteryInformation(registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        binding.checkNowButton.setTrackedClickListener(EventUtils.CLICK_FIRST_BATTERY_CHECK) { navigateTo(BatteryCheckScanActivity::class.java) }
        trackOnboardingAnimation(binding.checkNowButton)
        lifecycleScope.launch {
            showAd(AppAdManager.ONBOARDING_ANALYSIS)
        }
    }

    private fun renderBatteryInformation(battery: Intent?) {
        binding.batteryStatusValue.setText(R.string.string_onboarding_battery_status_pending)
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = level * 100 / scale.coerceAtLeast(1)
        binding.batteryPercent.text = getString(R.string.percentage_value, percent)
        val charging = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) in listOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)
        val manager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val charge = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val capacity = if (charge > 0 && percent > 0) (charge / 1000.0 * 100 / percent).toInt() else 0
        val current = kotlin.math.abs(manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toLong()) / 1000.0
        if (capacity > 0 && current >= 10) {
            val remaining = ((if (charging) 100 - percent else percent) / 100.0 * capacity / current * 60).toLong()
            val time = if (remaining >= 60) getString(R.string.string_onboarding_duration_hours, remaining / 60, remaining % 60)
                else getString(R.string.string_onboarding_duration_minutes, remaining.coerceAtLeast(1))
            binding.batteryEstimate.text = getString(if (charging) R.string.string_onboarding_battery_estimated_charging else R.string.string_onboarding_battery_estimated_remaining, time)
        }
        binding.temperatureCard.metricLabel.setText(R.string.temperature)
        binding.temperatureCard.metricIcon.setImageResource(R.drawable.onboarding_ic_battery_temperature_competitor)
        binding.temperatureCard.metricValue.text = getString(R.string.temperature_value_compact, (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0)
        binding.voltageCard.metricLabel.setText(R.string.voltage)
        binding.voltageCard.metricIcon.setImageResource(R.drawable.onboarding_ic_battery_voltage_competitor)
        binding.voltageCard.metricValue.text = getString(R.string.string_onboarding_voltage, battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0)
        binding.typeCard.metricLabel.setText(R.string.battery_type)
        binding.typeCard.metricIcon.setImageResource(R.drawable.onboarding_ic_battery_type_competitor)
        binding.typeCard.metricValue.text = battery?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty().ifBlank { getString(R.string.string_onboarding_literal_2) }
        binding.powerCard.metricLabel.setText(R.string.string_onboarding_battery_power_label)
        binding.powerCard.metricIcon.setImageResource(R.drawable.onboarding_ic_battery_power_competitor)
        binding.powerCard.metricValue.text = if (capacity > 0) getString(R.string.capacity_value, capacity) else getString(R.string.string_onboarding_literal_2)
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
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

}
