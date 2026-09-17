package com.nice.aceclean.ui.main

import android.content.Intent
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.cleanup.CleanupCategory
import com.nice.aceclean.cleanup.CleanupViewModel
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.util.DeviceStats
import kotlinx.coroutines.launch

class CleanResultFragment : BaseFragment(R.layout.fragment_clean_result) {

    private val viewModel: CleanupViewModel by activityViewModels()
    private val selected = CleanupCategory.entries.toMutableSet()
    private lateinit var rootView: View

    override fun initViews(root: View) {
        rootView = root
        root.findViewById<View>(R.id.clean_result_back).setOnClickListener {
            requireActivity().finish()
        }
        bindCategory(R.id.cleanup_cache_row, R.id.cleanup_cache_size, R.id.cleanup_cache_check, CleanupCategory.APP_CACHE)
        bindCategory(R.id.cleanup_temp_row, R.id.cleanup_temp_size, R.id.cleanup_temp_check, CleanupCategory.TEMP_FILES)
        bindCategory(R.id.cleanup_apk_row, R.id.cleanup_apk_size, R.id.cleanup_apk_check, CleanupCategory.APK_FILES)
        bindCategory(R.id.cleanup_empty_row, R.id.cleanup_empty_size, R.id.cleanup_empty_check, CleanupCategory.EMPTY_FILES)
        root.findViewById<View>(R.id.cleanup_more_row).visibility = View.GONE
        root.findViewById<View>(R.id.remove_button).setOnClickListener { confirmDelete() }
        root.findViewById<View>(R.id.cleanup_rescan).setOnClickListener {
            (activity as? CleanUpActivity)?.restartScan()
        }
        root.findViewById<View>(R.id.cleanup_find_large_files).setOnClickListener {
            startActivity(Intent(requireContext(), BigFileCleanerActivity::class.java))
        }
        root.findViewById<View>(R.id.cleanup_system_cache).apply {
            visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) View.VISIBLE else View.GONE
            setOnClickListener { openSystemCacheCleaner() }
        }
        render()
    }

    private fun bindCategory(rowId: Int, sizeId: Int, checkId: Int, category: CleanupCategory) {
        rootView.findViewById<View>(rowId).setOnClickListener {
            if (!selected.add(category)) selected.remove(category)
            render()
        }
        rootView.findViewById<TextView>(sizeId).text = DeviceStats.formatBytes(requireContext(), viewModel.scanResult?.size(category) ?: 0L)
        rootView.findViewById<ImageView>(checkId).isSelected = category in selected
    }

    private fun render() {
        val result = viewModel.scanResult ?: return
        val isEmpty = result.candidates.isEmpty()
        rootView.findViewById<TextView>(R.id.cleanup_total_size).apply {
            visibility = if (isEmpty) View.GONE else View.VISIBLE
            text = if (result.totalBytes == 0L && result.candidates.isNotEmpty()) {
                getString(R.string.items_found, result.candidates.size)
            } else {
                DeviceStats.formatBytes(requireContext(), result.totalBytes)
            }
        }
        rootView.findViewById<View>(R.id.cleanup_removable_label).visibility = if (isEmpty) View.GONE else View.VISIBLE
        rootView.findViewById<View>(R.id.cleanup_empty_state).visibility = if (isEmpty) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.remove_button).visibility = if (isEmpty) View.GONE else View.VISIBLE
        rootView.findViewById<TextView>(R.id.cleanup_scan_details).text = getString(
            R.string.clean_scan_summary,
            result.stats.scannedFiles,
            result.stats.scannedDirectories,
        )
        rootView.findViewById<TextView>(R.id.cleanup_scan_warning).apply {
            visibility = if (result.stats.limitReached || result.stats.inaccessibleDirectories > 0) View.VISIBLE else View.GONE
            text = if (result.stats.limitReached) {
                getString(R.string.scan_incomplete)
            } else {
                getString(R.string.scan_restricted_summary, result.stats.inaccessibleDirectories)
            }
        }
        val categories = listOf(
            CategoryViews(CleanupCategory.APP_CACHE, R.id.cleanup_cache_row, R.id.cleanup_cache_check, R.id.cleanup_cache_size),
            CategoryViews(CleanupCategory.TEMP_FILES, R.id.cleanup_temp_row, R.id.cleanup_temp_check, R.id.cleanup_temp_size),
            CategoryViews(CleanupCategory.APK_FILES, R.id.cleanup_apk_row, R.id.cleanup_apk_check, R.id.cleanup_apk_size),
            CategoryViews(CleanupCategory.EMPTY_FILES, R.id.cleanup_empty_row, R.id.cleanup_empty_check, R.id.cleanup_empty_size),
        )
        categories.forEach { (category, rowId, checkId, sizeId) ->
            rootView.findViewById<View>(rowId).visibility = if (isEmpty) View.GONE else View.VISIBLE
            rootView.findViewById<View>(checkId).alpha = if (category in selected) 1f else 0.25f
            val candidates = result.candidates(category)
            rootView.findViewById<TextView>(sizeId).text = if (candidates.isNotEmpty() && result.size(category) == 0L) {
                getString(R.string.items_found, candidates.size)
            } else {
                DeviceStats.formatBytes(requireContext(), result.size(category))
            }
        }
        val selectedBytes = result.candidates.filter { it.category in selected }.sumOf { it.sizeBytes }
        rootView.findViewById<TextView>(R.id.remove_button).text = getString(
            R.string.remove_selected_size,
            DeviceStats.formatBytes(requireContext(), selectedBytes),
        )
    }

    private fun confirmDelete() {
        val candidates = viewModel.scanResult?.candidates?.filter { it.category in selected }.orEmpty()
        if (candidates.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_junk, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clean_confirm_title)
            .setMessage(getString(R.string.clean_confirm_message, candidates.size, DeviceStats.formatBytes(requireContext(), candidates.sumOf { it.sizeBytes })))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clean) { _, _ -> deleteSelected() }
            .show()
    }

    private fun deleteSelected() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.delete(selected)
            Toast.makeText(
                requireContext(),
                getString(R.string.clean_result_message, result.deletedCount, DeviceStats.formatBytes(requireContext(), result.releasedBytes), result.failedCount),
                Toast.LENGTH_LONG,
            ).show()
            render()
        }
    }

    private fun openSystemCacheCleaner() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
        } else {
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        }
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
    }

    private data class CategoryViews(
        val category: CleanupCategory,
        val rowId: Int,
        val checkId: Int,
        val sizeId: Int,
    )
}
