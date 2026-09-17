package com.nice.aceclean.ui.main

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.NetworkUsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkTrafficFragment : BaseFragment(R.layout.fragment_network_traffic) {

    override fun initViews(root: View) {
        root.findViewById<View>(R.id.network_back).setOnClickListener { requireActivity().finish() }
        loadTraffic(root)
    }

    private fun loadTraffic(root: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val usage = NetworkUsageRepository.currentMonth(requireContext())
                val apps = DeviceStats.launchableApps(requireContext())
                    .map { app -> app.copy(rxBytes = usage.bytesByUid[app.applicationInfo.uid] ?: 0L, txBytes = 0L) }
                    .filter { it.rxBytes > 0L }
                    .sortedByDescending { it.rxBytes }
                    .take(8)
                usage to apps
            }
            if (!isAdded) return@launch
            val (usage, apps) = result

            root.findViewById<TextView>(R.id.network_total).text = DeviceStats.formatBytes(requireContext(), usage.totalBytes)
            root.findViewById<TextView>(R.id.network_mobile).text = DeviceStats.formatBytes(requireContext(), usage.mobileBytes)
            root.findViewById<TextView>(R.id.network_wifi).text = DeviceStats.formatBytes(requireContext(), usage.wifiBytes)
            renderDailyBars(root, usage.dailyBytes)

            val container = root.findViewById<LinearLayout>(R.id.network_app_list)
            container.removeAllViews()
            if (apps.isEmpty()) {
                TextView(requireContext()).apply {
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
                val item = LayoutInflater.from(requireContext()).inflate(R.layout.item_network_app, container, false)
                item.findViewById<ImageView>(R.id.network_app_icon).setImageDrawable(requireContext().packageManager.getApplicationIcon(app.applicationInfo))
                item.findViewById<TextView>(R.id.network_app_name).text = app.label
                item.findViewById<TextView>(R.id.network_app_package).text = app.packageName
                item.findViewById<TextView>(R.id.network_app_usage).text = DeviceStats.formatBytes(requireContext(), app.rxBytes + app.txBytes)
                container.addView(item)
            }
        }
    }

    private fun renderDailyBars(root: View, dailyBytes: List<Long>) {
        val barIds = intArrayOf(
            R.id.network_day_1, R.id.network_day_2, R.id.network_day_3, R.id.network_day_4,
            R.id.network_day_5, R.id.network_day_6, R.id.network_day_7,
        )
        val maximum = dailyBytes.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val minimumHeight = (8 * resources.displayMetrics.density).toInt()
        val maximumHeight = (104 * resources.displayMetrics.density).toInt()
        barIds.forEachIndexed { index, id ->
            val value = dailyBytes.getOrElse(index) { 0L }
            val bar = root.findViewById<View>(id)
            bar.layoutParams = bar.layoutParams.apply {
                height = if (value <= 0L) minimumHeight else
                    (minimumHeight + (maximumHeight - minimumHeight) * value / maximum).toInt()
            }
        }
    }
}
