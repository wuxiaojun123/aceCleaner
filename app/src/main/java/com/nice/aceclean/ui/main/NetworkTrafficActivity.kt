package com.nice.aceclean.ui.main

import androidx.activity.viewModels
import com.nice.aceclean.ui.base.BaseActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.FragmentNetworkTrafficBinding
import com.nice.aceclean.databinding.ItemNetworkAppBinding
import com.nice.aceclean.util.InstalledAppInfo
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.EventUtils

class NetworkTrafficActivity : BaseActivity<FragmentNetworkTrafficBinding>(
    { FragmentNetworkTrafficBinding.inflate(it) },
    R.layout.fragment_network_traffic,
) {
    private val appAdapter = NetworkAppAdapter()

    private val handoff: FeatureResultViewModel by viewModels()

    override fun initViews() {
        enableBackInterstitial()
        val result = scanResult(handoff, ScanFeature.NETWORK_TRAFFIC) as? FeatureScanResult.Traffic ?: return
        binding.networkBack.setTrackedClickListener(EventUtils.CLICK_NETWORK_BACK) { finishWithBackInterstitial() }
        binding.networkAppList.apply {
            layoutManager = LinearLayoutManager(this@NetworkTrafficActivity)
            adapter = appAdapter
        }
        val usage = result.usage
        binding.networkTotal.text = DeviceStats.formatBytes(this, usage.totalBytes)
        binding.networkMobile.text = DeviceStats.formatBytes(this, usage.mobileBytes)
        binding.networkWifi.text = DeviceStats.formatBytes(this, usage.wifiBytes)
        renderDailyBars(usage.dailyBytes)
        binding.networkAppEmpty.visibility = if (result.apps.isEmpty()) View.VISIBLE else View.GONE
        binding.networkAppList.visibility = if (result.apps.isEmpty()) View.GONE else View.VISIBLE
        appAdapter.submitList(result.apps) { binding.networkContent.visibility = View.VISIBLE }
    }

    private fun renderDailyBars(dailyBytes: List<Long>) {
        val bars = listOf(
            binding.networkDay1, binding.networkDay2, binding.networkDay3,
            binding.networkDay4, binding.networkDay5, binding.networkDay6,
            binding.networkDay7,
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

// ActivityGuard relocates this class separately from the Activity; it must stay JVM-public.
internal class NetworkAppAdapter : ListAdapter<InstalledAppInfo, NetworkAppAdapter.ViewHolder>(DIFF_CALLBACK) {
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
