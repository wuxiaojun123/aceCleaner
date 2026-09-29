package com.nice.aceclean.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.InstalledAppInfo
import com.nice.aceclean.util.NetworkUsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkTrafficActivity : PermissionFeatureActivity(
    FeatureType.NETWORK_TRAFFIC,
    R.layout.fragment_network_traffic,
) {
    private val appAdapter = NetworkAppAdapter()

    override fun initFeatureViews() {
        view<View>(R.id.network_back).setOnClickListener { finish() }
        view<RecyclerView>(R.id.network_app_list).apply {
            layoutManager = LinearLayoutManager(this@NetworkTrafficActivity)
            adapter = appAdapter
        }
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
            view<View>(R.id.network_app_empty).visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            view<RecyclerView>(R.id.network_app_list).visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
            appAdapter.submitList(apps) {
                view<View>(R.id.network_content).visibility = View.VISIBLE
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

private class NetworkAppAdapter : ListAdapter<InstalledAppInfo, NetworkAppAdapter.ViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val item = LayoutInflater.from(parent.context).inflate(R.layout.item_network_app, parent, false)
        return ViewHolder(item)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.network_app_icon)
        private val name: TextView = itemView.findViewById(R.id.network_app_name)
        private val packageName: TextView = itemView.findViewById(R.id.network_app_package)
        private val usage: TextView = itemView.findViewById(R.id.network_app_usage)

        fun bind(app: InstalledAppInfo) {
            icon.setImageDrawable(itemView.context.packageManager.getApplicationIcon(app.applicationInfo))
            name.text = app.label
            packageName.text = app.packageName
            usage.text = DeviceStats.formatBytes(itemView.context, app.rxBytes + app.txBytes)
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<InstalledAppInfo>() {
            override fun areItemsTheSame(oldItem: InstalledAppInfo, newItem: InstalledAppInfo) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: InstalledAppInfo, newItem: InstalledAppInfo) =
                oldItem == newItem
        }
    }
}
