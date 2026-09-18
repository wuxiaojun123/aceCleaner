package com.nice.aceclean.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.icu.util.Calendar
import android.net.Uri
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
import com.nice.aceclean.databinding.ItemAppManagerBinding
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.InstalledAppInfo
import com.nice.aceclean.util.NetworkSpeedSampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppManagerActivity : PermissionFeatureActivity(
    FeatureType.APP_MANAGER,
    R.layout.fragment_app_manager,
) {

    private val uninstallQueue = ArrayDeque<String>()
    private lateinit var rootView: View
    private var apps: List<InstalledAppInfo> = emptyList()
    private var sortMode = SortMode.NAME
    private var sortOrder = SortOrder.ASCENDING

    private val appAdapter = ManagedAppAdapter(::onSelectionChanged)

    private fun onSelectionChanged(selectedApps: List<InstalledAppInfo>) = updateButton(selectedApps.size)

    private val uninstallLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            uninstallQueue.clear()
            Toast.makeText(this, R.string.uninstall_cancelled, Toast.LENGTH_SHORT).show()
            loadApps()
        } else if (uninstallQueue.isNotEmpty()) {
            uninstallNext()
        } else {
            loadApps()
        }
    }

    override fun initFeatureViews() {
        rootView = findViewById(android.R.id.content)
        view<View>(R.id.app_manager_back).setOnClickListener { finish() }
        view<RecyclerView>(R.id.app_manager_list).apply {
            layoutManager = LinearLayoutManager(this@AppManagerActivity)
            adapter = appAdapter
            setHasFixedSize(true)
        }
        bindSortControls(rootView)
        view<View>(R.id.app_manager_uninstall).setOnClickListener {
            val selectedApps = appAdapter.selectedApps()
            if (selectedApps.isEmpty()) {
                Toast.makeText(this, R.string.select_apps_first, Toast.LENGTH_SHORT).show()
            } else {
                uninstallQueue.clear()
                uninstallQueue.addAll(selectedApps.map(InstalledAppInfo::packageName))
                uninstallNext()
            }
        }
        loadApps()
    }

    private fun loadApps() {
        if (!::rootView.isInitialized) return
        lifecycleScope.launch {
            val loadedApps = withContext(Dispatchers.IO) { DeviceStats.launchableApps(this@AppManagerActivity) }
            if (isFinishing || isDestroyed) return@launch
            apps = loadedApps
            appAdapter.clearSelection()
            updateButton(0)
            renderApps()
        }
    }

    private fun bindSortControls(root: View) {
        mapOf(
            R.id.app_manager_sort_name to SortMode.NAME,
            R.id.app_manager_sort_installation to SortMode.INSTALLATION,
            R.id.app_manager_sort_size to SortMode.SIZE,
            R.id.app_manager_sort_last_used to SortMode.LAST_USED,
        ).forEach { (viewId, mode) ->
            root.findViewById<View>(viewId).setOnClickListener {
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
        if (!::rootView.isInitialized) return
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
            rootView.findViewById<TextView>(viewId).apply {
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
        if (!::rootView.isInitialized) return
        rootView.findViewById<TextView>(R.id.app_manager_uninstall).text = if (selectedCount == 0) {
            getString(R.string.uninstall)
        } else {
            getString(R.string.uninstall_count, selectedCount)
        }
    }

    private fun uninstallNext() {
        if (uninstallQueue.isEmpty()) {
            loadApps()
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
            appItemRoot.setOnClickListener {
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

