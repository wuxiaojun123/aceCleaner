package com.nice.aceclean.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.icu.util.Calendar
import android.net.Uri
import androidx.activity.viewModels
import com.nice.aceclean.ui.base.BaseActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.FragmentAppManagerBinding
import com.nice.aceclean.databinding.ItemAppManagerBinding
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.EventUtils
import com.nice.aceclean.util.InstalledAppInfo
import com.nice.aceclean.util.NetworkSpeedSampler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppManagerActivity : BaseActivity<FragmentAppManagerBinding>(
    { FragmentAppManagerBinding.inflate(it) },
    R.layout.fragment_app_manager,
) {

    private val uninstallQueue = ArrayDeque<String>()
    private var uninstalledCount = 0
    private var apps: List<InstalledAppInfo> = emptyList()
    private var sortMode = SortMode.NAME
    private var sortOrder = SortOrder.ASCENDING

    private val appAdapter = ManagedAppAdapter(::onSelectionChanged)

    private fun onSelectionChanged(selectedApps: List<InstalledAppInfo>) = updateButton(selectedApps.size)

    private val uninstallLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            uninstallQueue.clear()
            Toast.makeText(this, R.string.uninstall_cancelled, Toast.LENGTH_SHORT).show()
            finishUninstall()
        } else {
            uninstalledCount++
            uninstallNext()
        }
    }

    private val handoff: FeatureResultViewModel by viewModels()

    override fun initViews() {
        enableBackInterstitial()
        val result = scanResult(handoff, ScanFeature.APP_MANAGER) as? FeatureScanResult.Apps ?: return
        binding.appManagerBack.setTrackedClickListener(EventUtils.CLICK_APPS_BACK) { finishWithBackInterstitial() }
        binding.appManagerList.apply {
            layoutManager = LinearLayoutManager(this@AppManagerActivity)
            adapter = appAdapter
            setHasFixedSize(true)
        }
        bindSortControls()
        binding.appManagerUninstall.setTrackedClickListener(EventUtils.CLICK_APPS_UNINSTALL) {
            val selectedApps = appAdapter.selectedApps()
            if (selectedApps.isEmpty()) {
                Toast.makeText(this, R.string.select_apps_first, Toast.LENGTH_SHORT).show()
            } else {
                uninstallQueue.clear()
                uninstalledCount = 0
                uninstallQueue.addAll(selectedApps.map(InstalledAppInfo::packageName))
                uninstallNext()
            }
        }
        apps = result.apps
        updateButton(0)
        renderApps()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val loadedApps = withContext(Dispatchers.IO) { DeviceStats.launchableApps(this@AppManagerActivity) }
            if (isFinishing || isDestroyed) return@launch
            handoff.update(FeatureScanResult.Apps(loadedApps))
            apps = loadedApps
            appAdapter.clearSelection()
            updateButton(0)
            renderApps()
        }
    }

    private fun sortControl(id: Int): TextView = when (id) {
        R.id.app_manager_sort_name -> binding.appManagerSortName
        R.id.app_manager_sort_installation -> binding.appManagerSortInstallation
        R.id.app_manager_sort_size -> binding.appManagerSortSize
        R.id.app_manager_sort_last_used -> binding.appManagerSortLastUsed
        else -> error("Unknown sort control: $id")
    }

    private fun bindSortControls() {
        mapOf(
            R.id.app_manager_sort_name to SortMode.NAME,
            R.id.app_manager_sort_installation to SortMode.INSTALLATION,
            R.id.app_manager_sort_size to SortMode.SIZE,
            R.id.app_manager_sort_last_used to SortMode.LAST_USED,
        ).forEach { (viewId, mode) ->
            val sortEvent = when (mode) {
                SortMode.NAME -> EventUtils.CLICK_APPS_SORT_NAME
                SortMode.INSTALLATION -> EventUtils.CLICK_APPS_SORT_INSTALLED
                SortMode.SIZE -> EventUtils.CLICK_APPS_SORT_SIZE
                SortMode.LAST_USED -> EventUtils.CLICK_APPS_SORT_LAST_USED
            }
            sortControl(viewId).setTrackedClickListener(sortEvent) {
                if (sortMode == mode) {
                    sortOrder = sortOrder.toggle()
                } else {
                    sortMode = mode
                    sortOrder = mode.defaultOrder
                }
                renderApps()
            }
        }
        updateSortControls()
    }

    private fun renderApps() {
        updateSortControls()
        appAdapter.submitList(sortedApps())
    }

    private fun sortedApps(): List<InstalledAppInfo> = when (sortMode) {
        SortMode.NAME -> if (sortOrder == SortOrder.ASCENDING) {
            apps.sortedBy { it.label.lowercase(Locale.getDefault()) }
        } else {
            apps.sortedByDescending { it.label.lowercase(Locale.getDefault()) }
        }

        SortMode.INSTALLATION -> apps.sortedByOrder(
            value = { it.installTime },
            order = sortOrder,
        )

        SortMode.SIZE -> apps.sortedByOrder(
            value = { it.sizeBytes },
            order = sortOrder,
        )

        SortMode.LAST_USED -> apps.sortedByOrder(
            value = { it.lastUsed },
            order = sortOrder,
        )
    }

    private fun List<InstalledAppInfo>.sortedByOrder(
        value: (InstalledAppInfo) -> Long,
        order: SortOrder,
    ): List<InstalledAppInfo> {
        val comparator = if (order == SortOrder.ASCENDING) {
            compareBy(value)
        } else {
            compareByDescending(value)
        }.thenBy { it.label.lowercase(Locale.getDefault()) }
        return sortedWith(comparator)
    }

    private fun updateSortControls() {
        val controls = mapOf(
            R.id.app_manager_sort_name to SortMode.NAME,
            R.id.app_manager_sort_installation to SortMode.INSTALLATION,
            R.id.app_manager_sort_size to SortMode.SIZE,
            R.id.app_manager_sort_last_used to SortMode.LAST_USED,
        )
        controls.forEach { (viewId, mode) ->
            val selected = mode == sortMode
            sortControl(viewId).apply {
                setBackgroundResource(if (selected) R.drawable.bg_app_sort_selected else R.drawable.bg_app_sort_unselected)
                setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF747681.toInt())
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    0,
                    if (!selected) {
                        R.drawable.ic_app_sort_neutral
                    } else if (sortOrder == SortOrder.ASCENDING) {
                        R.drawable.ic_app_sort_ascending
                    } else {
                        R.drawable.ic_app_sort_descending
                    },
                    0,
                )
            }
        }
    }

    private fun updateButton(selectedCount: Int = appAdapter.selectedApps().size) {
        binding.appManagerUninstall.text = if (selectedCount == 0) {
            getString(R.string.uninstall)
        } else {
            getString(R.string.uninstall_count, selectedCount)
        }
    }

    private fun uninstallNext() {
        if (uninstallQueue.isEmpty()) {
            finishUninstall()
            return
        }
        val packageName = uninstallQueue.removeFirst()
        uninstallLauncher.launch(
            @Suppress("DEPRECATION")
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName")).apply {
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            },
        )
    }

    private fun finishUninstall() {
        loadApps()
        DeleteSuccessActivity.show(
            context = this,
            removedCount = uninstalledCount,
            freedBytes = 0L,
            source = DeleteSuccessActivity.SOURCE_APP_MANAGER,
        )
        uninstalledCount = 0
    }

    private enum class SortMode(val defaultOrder: SortOrder) {
        NAME(SortOrder.ASCENDING),
        INSTALLATION(SortOrder.DESCENDING),
        SIZE(SortOrder.DESCENDING),
        LAST_USED(SortOrder.DESCENDING),
    }

    private enum class SortOrder {
        ASCENDING,
        DESCENDING;

        fun toggle(): SortOrder = if (this == ASCENDING) DESCENDING else ASCENDING
    }
}

private class ManagedAppAdapter(
    private val onSelectionChanged: (List<InstalledAppInfo>) -> Unit,
) : RecyclerView.Adapter<ManagedAppAdapter.ManagedAppViewHolder>() {

    private var apps: List<InstalledAppInfo> = emptyList()
    private val selectedPackages = linkedSetOf<String>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newApps: List<InstalledAppInfo>) {
        apps = newApps
        selectedPackages.retainAll(newApps.mapTo(hashSetOf()) { it.applicationInfo.packageName})
        notifyDataSetChanged()
    }

    fun selectedApps(): List<InstalledAppInfo> {
        return apps.filter { it.applicationInfo.packageName in selectedPackages }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        if (selectedPackages.isEmpty()) return
        selectedPackages.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount() = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ManagedAppViewHolder(
        ItemAppManagerBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ManagedAppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    inner class ManagedAppViewHolder(
        private val binding: ItemAppManagerBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: InstalledAppInfo) = with(binding) {
            val context = root.context
            appNameText.text = app.label
            appSizeText.text = NetworkSpeedSampler.format(app.sizeBytes)
            installDateText.text = context.getString(R.string.app_manager_installed, app.installedOn)
            lastUsedText.text = context.getString(
                R.string.app_manager_last_used,
                formatLastUsed(context, app.lastUsed),
            )
            appManagerIcon.setImageDrawable(
                runCatching { app.applicationInfo.loadIcon(context.packageManager) }.getOrNull(),
            )

            val selected = app.packageName in selectedPackages
            appManagerCheck.isChecked = selected
            appItemRoot.setBackgroundResource(
                if (selected) R.drawable.bg_app_manager_item_selected else R.drawable.bg_home_card,
            )
            appItemRoot.setTrackedClickListener(EventUtils.CLICK_APPS_SELECT) {
                if (!selectedPackages.add(app.packageName)) selectedPackages.remove(app.packageName)
                bindingAdapterPosition
                    .takeIf { it != RecyclerView.NO_POSITION }
                    ?.let(::notifyItemChanged)
                onSelectionChanged(selectedApps())
            }
        }
    }

    private fun formatLastUsed(context: Context, time: Long): String {
        if (time <= 0L) return context.getString(R.string.app_manager_last_used_unknown)
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (time >= startOfToday) return context.getString(R.string.app_manager_last_used_today)
        return SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(time))
    }
}

