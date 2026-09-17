package com.nice.aceclean.ui.main

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AppManagerFragment : BaseFragment(R.layout.fragment_app_manager) {

    private val selectedPackages = linkedSetOf<String>()
    private val uninstallQueue = ArrayDeque<String>()
    private lateinit var rootView: View
    private var apps: List<InstalledAppInfo> = emptyList()
    private var sortMode = SortMode.NAME
    private var sortOrder = SortOrder.ASCENDING

    private val uninstallLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            uninstallQueue.clear()
            Toast.makeText(requireContext(), R.string.uninstall_cancelled, Toast.LENGTH_SHORT).show()
            loadApps()
        } else if (uninstallQueue.isNotEmpty()) {
            uninstallNext()
        } else {
            loadApps()
        }
    }

    override fun initViews(root: View) {
        rootView = root
        root.findViewById<View>(R.id.app_manager_back).setOnClickListener { requireActivity().finish() }
        bindSortControls(root)
        root.findViewById<View>(R.id.app_manager_uninstall).setOnClickListener {
            if (selectedPackages.isEmpty()) {
                Toast.makeText(requireContext(), R.string.select_apps_first, Toast.LENGTH_SHORT).show()
            } else {
                uninstallQueue.clear()
                uninstallQueue.addAll(selectedPackages)
                uninstallNext()
            }
        }
        loadApps()
    }

    private fun loadApps() {
        if (!::rootView.isInitialized) return
        viewLifecycleOwner.lifecycleScope.launch {
            val loadedApps = withContext(Dispatchers.IO) { DeviceStats.launchableApps(requireContext()) }
            if (!isAdded) return@launch
            apps = loadedApps
            selectedPackages.clear()
            updateButton()
            rootView.findViewById<TextView>(R.id.app_manager_count).text = getString(R.string.apps_found, apps.size)
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
        val container = rootView.findViewById<LinearLayout>(R.id.app_manager_list)
        container.removeAllViews()
        sortedApps().forEach { app ->
            val item = LayoutInflater.from(requireContext()).inflate(R.layout.item_app_manager, container, false)
            item.findViewById<ImageView>(R.id.app_manager_icon).setImageDrawable(requireContext().packageManager.getApplicationIcon(app.applicationInfo))
            item.findViewById<TextView>(R.id.app_manager_name).text = app.label
            item.findViewById<TextView>(R.id.app_manager_meta).text = getString(
                R.string.last_used_value,
                DeviceStats.formatLastUsed(app.lastUsed, getString(R.string.never_used)),
            )
            item.findViewById<TextView>(R.id.app_manager_size).text = DeviceStats.formatBytes(requireContext(), app.sizeBytes)
            val check = item.findViewById<CheckBox>(R.id.app_manager_check)
            check.isChecked = app.packageName in selectedPackages
            updateItemSelection(item, check.isChecked)
            check.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedPackages.add(app.packageName) else selectedPackages.remove(app.packageName)
                updateItemSelection(item, isChecked)
                updateButton()
            }
            item.setOnClickListener { check.toggle() }
            container.addView(item)
        }
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

    private fun updateItemSelection(item: View, selected: Boolean) {
        item.setBackgroundResource(if (selected) R.drawable.bg_app_manager_item_selected else R.drawable.bg_home_card)
    }

    private fun updateButton() {
        if (!::rootView.isInitialized) return
        rootView.findViewById<TextView>(R.id.app_manager_uninstall).text = if (selectedPackages.isEmpty()) {
            getString(R.string.uninstall)
        } else {
            getString(R.string.uninstall_count, selectedPackages.size)
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
