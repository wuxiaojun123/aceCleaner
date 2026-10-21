package com.nice.aceclean.ui.main

import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.ad.HotStartManager
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.ActivityRamStatusBinding
import com.nice.aceclean.databinding.ItemRunningAppBinding
import com.nice.aceclean.ui.dialog.IosActionSheetDialog
import com.nice.aceclean.util.DeviceToolsRepository
import com.nice.aceclean.util.EventUtils
import com.nice.aceclean.util.RunningAppInfo
import com.nice.aceclean.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RamStatusActivity : DeviceToolActivity<ActivityRamStatusBinding>({ ActivityRamStatusBinding.inflate(it) }, R.layout.activity_ram_status, R.string.ram_status, EventUtils.CLICK_RAM_BACK) {
    private val runningAppsAdapter = RunningAppsAdapter(::openAppSettings)
    private val handoff: FeatureResultViewModel by viewModels()
    private var firstResume = true
    private var resultShown = false
    private var refreshJob: Job? = null
    private var stopAppTipsJob: Job? = null
    private val appSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        stopAppTipsJob?.cancel()
        DefaultLauncherTipsActivity.dismiss()
    }

    override val featureHeader get() = binding.featureHeader

    override fun initToolViews() {
        val result = scanResult(handoff, ScanFeature.RAM) as? FeatureScanResult.Ram ?: return
        binding.ramOkButton.setTrackedClickListener(EventUtils.CLICK_RAM_OK) { finishWithBackInterstitial() }
        binding.ramAppList.apply {
            layoutManager = LinearLayoutManager(this@RamStatusActivity)
            adapter = runningAppsAdapter
            val spacing = resources.getDimensionPixelSize(R.dimen.ram_app_row_spacing)
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    itemView: View,
                    parent: RecyclerView,
                    state: RecyclerView.State,
                ) {
                    outRect.bottom = spacing
                }
            })
        }
        renderMemory(result.memory)
        renderRunningApps(result.apps)
        binding.ramScanRing.cancelAnimation()
        binding.ramScanAnimation.cancelAnimation()
        binding.ramScanContainer.visibility = View.GONE
        binding.ramResultContainer.visibility = View.VISIBLE
        resultShown = true
    }

    override fun onResume() {
        super.onResume()
        if (!resultShown) return
        if (!ScanFeature.RAM.hasPermission(this)) {
            startActivity(ScanFeature.RAM.scanIntent(this))
            finish()
        } else if (firstResume) firstResume = false else refreshResult()
    }

    private fun refreshResult() {
        if (!resultShown) return
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                DeviceToolsRepository.runningApps(this@RamStatusActivity)
            }
            val memory = DeviceToolsRepository.memorySnapshot(this@RamStatusActivity)
            handoff.update(FeatureScanResult.Ram(memory, apps))
            renderMemory(memory)
            renderRunningApps(apps)
        }
    }

    private fun renderMemory(memory: com.nice.aceclean.util.MemorySnapshot) {
        val availablePercent = (100 - memory.usedPercent).coerceIn(0, 100)
        binding.ramPercent.text = getString(R.string.percentage_value, availablePercent)
    }

    private fun renderRunningApps(apps: List<RunningAppInfo>) {
        binding.ramUsagePermission.visibility = View.GONE
        binding.ramEmptyMessage.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        binding.ramRunningSummary.text = if (apps.isEmpty()) {
            getString(R.string.string_ram_no_recent_apps)
        } else {
            getString(R.string.string_ram_recent_apps_count, apps.size)
        }
        runningAppsAdapter.submitList(apps)
    }

    private fun openAppSettings(app: RunningAppInfo) {
        IosActionSheetDialog.show(
            activity = this,
            title = getString(R.string.force_stop_title),
            cancelEventName = EventUtils.CLICK_RAM_FORCE_STOP_CANCEL,
            message = getString(R.string.force_stop_explanation),
            actions = listOf(IosActionSheetDialog.Action(getString(R.string.continue_text), eventName = EventUtils.CLICK_RAM_FORCE_STOP_CONTINUE) {
                HotStartManager.markSkipNextHotStart()
                runCatching {
                    appSettingsLauncher.launch(Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${app.packageName}"),
                    ))
                }.onSuccess {
                    stopAppTipsJob?.cancel()
                    stopAppTipsJob = lifecycleScope.launch {
                        delay(350L)
                        if (isFinishing || isDestroyed ||
                            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                        runCatching { DefaultLauncherTipsActivity.startForStopApp(this@RamStatusActivity) }
                            .onFailure { LogUtils.w(tag = "RamStatus", msg = "Unable to show stop app tips", throwable = it) }
                    }
                }.onFailure {
                    LogUtils.w(tag = "RamStatus", msg = "Unable to open app settings", throwable = it)
                }
            }),
        )
    }

}

// ActivityGuard relocates this class separately from the Activity. Keep it JVM-public
// so the Activity can still access it after release package obfuscation.
internal class RunningAppsAdapter(
    private val onCloseClick: (RunningAppInfo) -> Unit,
) : ListAdapter<RunningAppInfo, RunningAppsAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRunningAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onCloseClick)
    }

    class ViewHolder(binding: ItemRunningAppBinding) : RecyclerView.ViewHolder(binding.root) {
        private val icon: ImageView = binding.runningAppIcon
        private val name: TextView = binding.runningAppName
        private val close: View = binding.runningAppClose

        fun bind(app: RunningAppInfo, onCloseClick: (RunningAppInfo) -> Unit) {
            icon.setImageDrawable(itemView.context.packageManager.getApplicationIcon(app.applicationInfo))
            name.text = app.label
            close.setTrackedClickListener(EventUtils.CLICK_RAM_CLOSE_APP) { onCloseClick(app) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RunningAppInfo>() {
            override fun areItemsTheSame(oldItem: RunningAppInfo, newItem: RunningAppInfo) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: RunningAppInfo, newItem: RunningAppInfo) =
                oldItem == newItem
        }
    }
}
