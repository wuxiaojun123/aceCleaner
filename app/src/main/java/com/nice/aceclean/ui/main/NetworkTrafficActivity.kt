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
import com.nice.aceclean.databinding.ItemNetworkAppBinding
import com.nice.aceclean.databinding.FragmentNetworkTrafficBinding
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.InstalledAppInfo
import com.nice.aceclean.util.NetworkUsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkTrafficActivity : PermissionFeatureActivity<FragmentNetworkTrafficBinding>(
    FeatureType.NETWORK_TRAFFIC,
    { FragmentNetworkTrafficBinding.inflate(it) },
    R.layout.fragment_network_traffic,
) {
    private val appAdapter = NetworkAppAdapter()

    override fun initFeatureViews() {
        featureBinding.networkBack.setOnClickListener { finish() }
        featureBinding.networkAppList.apply {
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

            featureBinding.networkTotal.text = DeviceStats.formatBytes(this@NetworkTrafficActivity, usage.totalBytes)
            featureBinding.networkMobile.text = DeviceStats.formatBytes(this@NetworkTrafficActivity, usage.mobileBytes)
            featureBinding.networkWifi.text = DeviceStats.formatBytes(this@NetworkTrafficActivity, usage.wifiBytes)
            renderDailyBars(usage.dailyBytes)
            featureBinding.networkAppEmpty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            featureBinding.networkAppList.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
            appAdapter.submitList(apps) {
                featureBinding.networkContent.visibility = View.VISIBLE
            }
        }
    }

    private fun renderDailyBars(dailyBytes: List<Long>) {
        val bars = listOf(
            featureBinding.networkDay1, featureBinding.networkDay2, featureBinding.networkDay3,
            featureBinding.networkDay4, featureBinding.networkDay5, featureBinding.networkDay6,
            featureBinding.networkDay7,
        )
        val maximum = dailyBytes.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val minimumHeight = (8 * resources.displayMetrics.density).toInt()
        val maximumHeight = (104 * resources.displayMetrics.density).toInt()
        bars.forEachIndexed { index, bar ->
            val value = dailyBytes.getOrElse(index) { 0L }
            bar.layoutParams = bar.layoutParams.apply {
                height = if (value <= 0L) minimumHeight else
                    (minimumHeight + (maximumHeight - minimumHeight) * value / maximum).toInt()
            }
        }
    }
}

private class NetworkAppAdapter : ListAdapter<InstalledAppInfo, NetworkAppAdapter.ViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNetworkAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(binding: ItemNetworkAppBinding) : RecyclerView.ViewHolder(binding.root) {
        private val icon: ImageView = binding.networkAppIcon
        private val name: TextView = binding.networkAppName
        private val packageName: TextView = binding.networkAppPackage
        private val usage: TextView = binding.networkAppUsage

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
