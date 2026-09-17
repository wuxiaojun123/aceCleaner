package com.nice.aceclean.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nice.aceclean.R
import com.nice.aceclean.cleanup.CleanupCategory
import com.nice.aceclean.cleanup.CleanupViewModel
import com.nice.aceclean.permission.StorageAccess
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.DeviceStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CleanUpActivity : BaseActivity(R.layout.fragment_clean_scan) {
    private val viewModel: CleanupViewModel by viewModels()
    private val selected = CleanupCategory.entries.toMutableSet()
    private var scanJob: Job? = null
    private var progressJob: Job? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (StorageAccess.hasBroadAccess(this)) startScan() else showPermissionRequired()
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (StorageAccess.hasBroadAccess(this)) startScan() else showPermissionRequired()
    }

    override fun initViews() {
        if (StorageAccess.hasBroadAccess(this)) startScan() else requestStorageAccess()
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                settingsLauncher.launch(StorageAccess.broadAccessSettingsIntent(this))
            } catch (_: ActivityNotFoundException) {
                settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else permissionLauncher.launch(StorageAccess.legacyPermissions())
    }

    private fun showPermissionRequired() {
        AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_required)
            .setMessage(R.string.storage_permission_explanation)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setNeutralButton(R.string.open_app_settings) { _, _ -> settingsLauncher.launch(StorageAccess.appSettingsIntent(this)) }
            .setPositiveButton(R.string.try_again) { _, _ -> requestStorageAccess() }
            .show()
    }

    private fun startScan() {
        scanJob?.cancel()
        progressJob?.cancel()
        setActivityContent(R.layout.fragment_clean_scan)
        val progressText = findViewById<TextView>(R.id.scan_percentage)
        progressText.text = getString(R.string.scanned_files_progress, 0)
        progressJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scanProgress.collect { progress ->
                    progressText.text = getString(R.string.scanned_files_progress, progress.scannedFiles)
                }
            }
        }
        scanJob = lifecycleScope.launch {
            viewModel.scan()
            progressJob?.cancel()
            progressJob = null
            showCleanResult()
        }
    }

    private fun showCleanResult() {
        setActivityContent(R.layout.fragment_clean_result)
        selected.clear()
        selected.addAll(CleanupCategory.entries)
        findViewById<View>(R.id.clean_result_back).setOnClickListener { finish() }
        bindCategory(R.id.cleanup_cache_row, R.id.cleanup_cache_size, R.id.cleanup_cache_check, CleanupCategory.APP_CACHE)
        bindCategory(R.id.cleanup_ad_row, R.id.cleanup_ad_size, R.id.cleanup_ad_check, CleanupCategory.AD_FILES)
        bindCategory(R.id.cleanup_temp_row, R.id.cleanup_temp_size, R.id.cleanup_temp_check, CleanupCategory.TEMP_FILES)
        bindCategory(R.id.cleanup_apk_row, R.id.cleanup_apk_size, R.id.cleanup_apk_check, CleanupCategory.APK_FILES)
        bindCategory(R.id.cleanup_residual_row, R.id.cleanup_residual_size, R.id.cleanup_residual_check, CleanupCategory.RESIDUAL_FILES)
        bindCategory(R.id.cleanup_empty_row, R.id.cleanup_empty_size, R.id.cleanup_empty_check, CleanupCategory.EMPTY_FILES)
        findViewById<View>(R.id.cleanup_more_row).setOnClickListener { openBigFileCleaner() }
        findViewById<View>(R.id.remove_button).setOnClickListener { confirmDelete() }
        findViewById<View>(R.id.cleanup_rescan).setOnClickListener { startScan() }
        findViewById<View>(R.id.cleanup_find_large_files).setOnClickListener { openBigFileCleaner() }
        findViewById<View>(R.id.cleanup_system_cache).apply {
            visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) View.VISIBLE else View.GONE
            setOnClickListener { openSystemCacheCleaner() }
        }
        render()
    }

    private fun openBigFileCleaner() = startActivity(Intent(this, BigFileCleanerActivity::class.java))

    private fun bindCategory(rowId: Int, sizeId: Int, checkId: Int, category: CleanupCategory) {
        findViewById<View>(rowId).setOnClickListener {
            if (!selected.add(category)) selected.remove(category)
            render()
        }
        findViewById<TextView>(sizeId).text = DeviceStats.formatBytes(this, viewModel.scanResult?.size(category) ?: 0L)
        findViewById<ImageView>(checkId).isSelected = category in selected
    }

    private fun render() {
        val result = viewModel.scanResult ?: return
        val isEmpty = result.candidates.isEmpty()
        findViewById<TextView>(R.id.cleanup_total_size).apply {
            visibility = if (isEmpty) View.GONE else View.VISIBLE
            text = if (result.totalBytes == 0L && result.candidates.isNotEmpty()) {
                getString(R.string.items_found, result.candidates.size)
            } else DeviceStats.formatBytes(this@CleanUpActivity, result.totalBytes)
        }
        findViewById<View>(R.id.cleanup_removable_label).visibility = if (isEmpty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.cleanup_empty_state).visibility = if (isEmpty) View.VISIBLE else View.GONE
        findViewById<View>(R.id.remove_button).visibility = if (isEmpty) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.cleanup_scan_details).text = getString(
            R.string.clean_scan_summary, result.stats.scannedFiles, result.stats.scannedDirectories,
        )
        findViewById<TextView>(R.id.cleanup_scan_warning).apply {
            visibility = if (result.stats.limitReached || result.stats.inaccessibleDirectories > 0) View.VISIBLE else View.GONE
            text = if (result.stats.limitReached) getString(R.string.scan_incomplete)
            else getString(R.string.scan_restricted_summary, result.stats.inaccessibleDirectories)
        }
        categoryViews.forEach { (category, rowId, checkId, sizeId) ->
            findViewById<View>(rowId).visibility = if (isEmpty) View.GONE else View.VISIBLE
            findViewById<View>(checkId).alpha = if (category in selected) 1f else 0.25f
            val candidates = result.candidates(category)
            findViewById<TextView>(sizeId).text = if (candidates.isNotEmpty() && result.size(category) == 0L) {
                getString(R.string.items_found, candidates.size)
            } else DeviceStats.formatBytes(this, result.size(category))
        }
        findViewById<View>(R.id.cleanup_more_row).visibility = if (isEmpty) View.GONE else View.VISIBLE
        val selectedBytes = result.candidates.filter { it.category in selected }.sumOf { it.sizeBytes }
        findViewById<TextView>(R.id.remove_button).text = getString(
            R.string.remove_selected_size, DeviceStats.formatBytes(this, selectedBytes),
        )
    }

    private fun confirmDelete() {
        val candidates = viewModel.scanResult?.candidates?.filter { it.category in selected }.orEmpty()
        if (candidates.isEmpty()) {
            Toast.makeText(this, R.string.no_junk, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.clean_confirm_title)
            .setMessage(getString(R.string.clean_confirm_message, candidates.size, DeviceStats.formatBytes(this, candidates.sumOf { it.sizeBytes })))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clean) { _, _ -> deleteSelected() }
            .show()
    }

    private fun deleteSelected() {
        lifecycleScope.launch {
            val result = viewModel.delete(selected)
            Toast.makeText(
                this@CleanUpActivity,
                getString(R.string.clean_result_message, result.deletedCount, DeviceStats.formatBytes(this@CleanUpActivity, result.releasedBytes), result.failedCount),
                Toast.LENGTH_LONG,
            ).show()
            render()
        }
    }

    private fun openSystemCacheCleaner() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
        } else Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
    }

    private data class CategoryViews(val category: CleanupCategory, val rowId: Int, val checkId: Int, val sizeId: Int)

    private val categoryViews = listOf(
        CategoryViews(CleanupCategory.APP_CACHE, R.id.cleanup_cache_row, R.id.cleanup_cache_check, R.id.cleanup_cache_size),
        CategoryViews(CleanupCategory.AD_FILES, R.id.cleanup_ad_row, R.id.cleanup_ad_check, R.id.cleanup_ad_size),
        CategoryViews(CleanupCategory.TEMP_FILES, R.id.cleanup_temp_row, R.id.cleanup_temp_check, R.id.cleanup_temp_size),
        CategoryViews(CleanupCategory.APK_FILES, R.id.cleanup_apk_row, R.id.cleanup_apk_check, R.id.cleanup_apk_size),
        CategoryViews(CleanupCategory.RESIDUAL_FILES, R.id.cleanup_residual_row, R.id.cleanup_residual_check, R.id.cleanup_residual_size),
        CategoryViews(CleanupCategory.EMPTY_FILES, R.id.cleanup_empty_row, R.id.cleanup_empty_check, R.id.cleanup_empty_size),
    )
}
