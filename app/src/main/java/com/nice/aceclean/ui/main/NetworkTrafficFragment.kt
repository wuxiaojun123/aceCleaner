package com.nice.aceclean.ui.main

import android.net.TrafficStats
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.util.DeviceStats
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
            val apps = withContext(Dispatchers.IO) {
                DeviceStats.launchableApps(requireContext())
                    .sortedByDescending { it.rxBytes + it.txBytes }
                    .take(8)
            }
            if (!isAdded) return@launch

            val total = (TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()).coerceAtLeast(0L)
            val mobile = (TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()).coerceAtLeast(0L)
            root.findViewById<TextView>(R.id.network_total).text = DeviceStats.formatBytes(total)
            root.findViewById<TextView>(R.id.network_mobile).text = DeviceStats.formatBytes(mobile)
            root.findViewById<TextView>(R.id.network_wifi).text = DeviceStats.formatBytes((total - mobile).coerceAtLeast(0L))

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
                item.findViewById<TextView>(R.id.network_app_usage).text = DeviceStats.formatBytes(app.rxBytes + app.txBytes)
                container.addView(item)
            }
        }
    }
}
