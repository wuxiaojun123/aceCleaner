package com.nice.aceclean.ui.main

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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.ActivityRamStatusBinding
import com.nice.aceclean.databinding.ItemRunningAppBinding
import com.nice.aceclean.permission.UsageAccessPermissionHelper
import com.nice.aceclean.ui.dialog.IosActionSheetDialog
import com.nice.aceclean.util.DeviceToolsRepository
import com.nice.aceclean.util.EventUtils
import com.nice.aceclean.util.RunningAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RamStatusActivity : DeviceToolActivity<ActivityRamStatusBinding>({ ActivityRamStatusBinding.inflate(it) }, R.layout.activity_ram_status, R.string.ram_status, EventUtils.CLICK_RAM_BACK) {
    private val runningAppsAdapter = RunningAppsAdapter(::openAppSettings)
    private lateinit var usageAccessPermission: UsageAccessPermissionHelper
    private var resultShown = false
    private var refreshJob: Job? = null

    override val featureHeader get() = binding.featureHeader

    override fun initToolViews() {
        usageAccessPermission = UsageAccessPermissionHelper(this, ::refreshResult)
        binding.ramUsageAllow.setTrackedClickListener(EventUtils.CLICK_RAM_ALLOW) { usageAccessPermission.request() }
        binding.ramOkButton.setTrackedClickListener(EventUtils.CLICK_RAM_OK) { finish() }
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
    }

    override fun initData() {
        lifecycleScope.launch {
            val appsDeferred = async(Dispatchers.IO) {
                DeviceToolsRepository.runningApps(this@RamStatusActivity)
            }
            for (progress in 1..100) {
                binding.ramScanPercent.text =
                    getString(R.string.percentage_value, progress)
                delay(SCAN_STEP_MILLIS)
            }
            val apps = appsDeferred.await()
            renderMemory()
            renderRunningApps(apps)
            binding.ramScanRing.cancelAnimation()
            binding.ramScanAnimation.cancelAnimation()
            binding.ramScanContainer.visibility = View.GONE
            binding.ramResultContainer.visibility = View.VISIBLE
            resultShown = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (resultShown) refreshResult()
    }

    private fun refreshResult() {
        if (!resultShown) return
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                DeviceToolsRepository.runningApps(this@RamStatusActivity)
            }
            renderMemory()
            renderRunningApps(apps)
        }
    }

    override fun onDestroy() {
        usageAccessPermission.close()
        super.onDestroy()
    }

    private fun renderMemory() {
        val memory = DeviceToolsRepository.memorySnapshot(this)
        val availablePercent = (100 - memory.usedPercent).coerceIn(0, 100)
        binding.ramPercent.text = getString(R.string.percentage_value, availablePercent)
    }

    private fun renderRunningApps(apps: List<RunningAppInfo>) {
        val hasUsageAccess = usageAccessPermission.isGranted()
        binding.ramUsagePermission.visibility = if (hasUsageAccess) View.GONE else View.VISIBLE
        binding.ramEmptyMessage.visibility = if (hasUsageAccess && apps.isEmpty()) View.VISIBLE else View.GONE
        binding.ramRunningSummary.text = if (!hasUsageAccess && apps.isEmpty()) {
            getString(R.string.string_ram_usage_access_required)
        } else if (apps.isEmpty()) {
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
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${app.packageName}"),
                    ),
                )
            }),
        )
    }

    private companion object {
        const val SCAN_STEP_MILLIS = 30L
    }
}

private class RunningAppsAdapter(
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
