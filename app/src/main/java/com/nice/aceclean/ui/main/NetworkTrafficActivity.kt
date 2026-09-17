package com.nice.aceclean.ui.main

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.NetworkUsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkTrafficActivity : PermissionFeatureActivity(
    FeatureType.NETWORK_TRAFFIC,
    R.layout.fragment_network_traffic,
) {

    override fun initFeatureViews() {
        view<View>(R.id.network_back).setOnClickListener { finish() }
        loadTraffic()
    }

    private fun loadTraffic() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val usage = NetworkUsageRepository.currentMonth(this@NetworkTrafficActivity)
                val apps = DeviceStats.launchableApps(this@NetworkTrafficActivity)
                    .map { app -> app.copy(rxBytes = usage.bytesByUid[app.applicationInfo.uid] ?: 0L, txBytes = 0L) }
                    .filter { it.rxBytes > 0L }
                    .sortedByDescending { it.rxBytes }
                    .take(8)
                usage to apps
            }
            if (isFinishing || isDestroyed) return@launch
            val (usage, apps) = result

            view<TextView>(R.id.network_total).text = DeviceStats.formatBytes(this@NetworkTrafficActivity, usage.totalBytes)
            view<TextView>(R.id.network_mobile).text = DeviceStats.formatBytes(this@NetworkTrafficActivity, usage.mobileBytes)
            view<TextView>(R.id.network_wifi).text = DeviceStats.formatBytes(this@NetworkTrafficActivity, usage.wifiBytes)
            renderDailyBars(usage.dailyBytes)

            val container = view<LinearLayout>(R.id.network_app_list)
            container.removeAllViews()
            if (apps.isEmpty()) {
                TextView(this@NetworkTrafficActivity).apply {
                    setText(R.string.no_usage_data)
                    textSize = 14f
                    setTextColor(0xFF92939A.toInt())
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 32, 0, 32)
                    container.addView(this)
                }
                return@launch
            }
            apps.forEach { app ->
                val item = LayoutInflater.from(this@NetworkTrafficActivity).inflate(R.layout.item_network_app, container, false)
                item.findViewById<ImageView>(R.id.network_app_icon).setImageDrawable(packageManager.getApplicationIcon(app.applicationInfo))
                item.findViewById<TextView>(R.id.network_app_name).text = app.label
                item.findViewById<TextView>(R.id.network_app_package).text = app.packageName
                item.findViewById<TextView>(R.id.network_app_usage).text = DeviceStats.formatBytes(this@NetworkTrafficActivity, app.rxBytes + app.txBytes)
                container.addView(item)
            }
        }
    }

    private fun renderDailyBars(dailyBytes: List<Long>) {
        val barIds = intArrayOf(
            R.id.network_day_1, R.id.network_day_2, R.id.network_day_3, R.id.network_day_4,
            R.id.network_day_5, R.id.network_day_6, R.id.network_day_7,
        )
        val maximum = dailyBytes.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val minimumHeight = (8 * resources.displayMetrics.density).toInt()
        val maximumHeight = (104 * resources.displayMetrics.density).toInt()
        barIds.forEachIndexed { index, id ->
            val value = dailyBytes.getOrElse(index) { 0L }
            val bar = view<View>(id)
            bar.layoutParams = bar.layoutParams.apply {
                height = if (value <= 0L) minimumHeight else
                    (minimumHeight + (maximumHeight - minimumHeight) * value / maximum).toInt()
            }
        }
    }
}
