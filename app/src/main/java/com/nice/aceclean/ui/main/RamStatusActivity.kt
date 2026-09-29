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
import com.nice.aceclean.ui.dialog.IosActionSheetDialog
import com.nice.aceclean.util.DeviceToolsRepository
import com.nice.aceclean.util.RunningAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RamStatusActivity : DeviceToolActivity(R.layout.activity_ram_status, R.string.ram_status) {
    private val runningAppsAdapter = RunningAppsAdapter(::openAppSettings)

    override fun initToolViews() {
        view<View>(R.id.ram_ok_button).setOnClickListener { finish() }
        view<RecyclerView>(R.id.ram_app_list).apply {
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
                view<TextView>(R.id.ram_scan_percent).text =
                    getString(R.string.percentage_value, progress)
                delay(SCAN_STEP_MILLIS)
            }
            val apps = appsDeferred.await()
            renderMemory()
            renderRunningApps(apps)
            view<com.airbnb.lottie.LottieAnimationView>(R.id.ram_scan_ring).cancelAnimation()
            view<com.airbnb.lottie.LottieAnimationView>(R.id.ram_scan_animation).cancelAnimation()
            view<View>(R.id.ram_scan_container).visibility = View.GONE
            view<View>(R.id.ram_result_container).visibility = View.VISIBLE
        }
    }

    private fun renderMemory() {
        val memory = DeviceToolsRepository.memorySnapshot(this)
        val availablePercent = (100 - memory.usedPercent).coerceIn(0, 100)
        view<TextView>(R.id.ram_percent).text = getString(R.string.percentage_value, availablePercent)
    }

    private fun renderRunningApps(apps: List<RunningAppInfo>) {
        view<TextView>(R.id.ram_running_summary).text = if (apps.isEmpty()) {
            getString(R.string.no_running_apps)
        } else {
            getString(R.string.running_apps_count, apps.size)
        }
        runningAppsAdapter.submitList(apps)
    }

    private fun openAppSettings(app: RunningAppInfo) {
        IosActionSheetDialog.show(
            activity = this,
            title = getString(R.string.force_stop_title),
            message = getString(R.string.force_stop_explanation),
            actions = listOf(IosActionSheetDialog.Action(getString(R.string.continue_text)) {
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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_running_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onCloseClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.running_app_icon)
        private val name: TextView = itemView.findViewById(R.id.running_app_name)
        private val close: View = itemView.findViewById(R.id.running_app_close)

        fun bind(app: RunningAppInfo, onCloseClick: (RunningAppInfo) -> Unit) {
            icon.setImageDrawable(itemView.context.packageManager.getApplicationIcon(app.applicationInfo))
            name.text = app.label
            close.setOnClickListener { onCloseClick(app) }
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
