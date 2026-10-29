package com.nice.aceclean.ui.main

import android.os.Build
import android.content.Intent
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.cleanup.CleanupCategory
import com.nice.aceclean.cleanup.CleanupViewModel
import com.nice.aceclean.databinding.FragmentCleanResultBinding
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.EventUtils

class CleanUpActivity : BaseActivity<FragmentCleanResultBinding>({ FragmentCleanResultBinding.inflate(it) }, R.layout.fragment_clean_result) {
    private val resultBinding get() = binding
    private val viewModel: CleanupViewModel by viewModels()
    private val selected = CleanupCategory.entries.toMutableSet()
    private var isDeleting = false
    private val cleaningLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDeleting = false
        render()
        // 成功时关闭旧扫描页，避免后台重扫为空后再次打开一个成功页。
        when (it.resultCode) {
            RESULT_OK -> finish()
            JunkCleaningActivity.RESULT_RESCAN -> startScan()
        }
    }

    private val handoff: FeatureResultViewModel by viewModels()

    override fun initViews() {
        enableBackInterstitial()
        if (viewModel.scanResult == null) {
            val result = scanResult(handoff, ScanFeature.CLEAN_UP) as? FeatureScanResult.Cleanup ?: return
            viewModel.restoreScan(result.value)
        }
        showCleanResult()
    }

    private fun startScan() {
        startActivity(ScanFeature.CLEAN_UP.scanIntent(this))
        finish()
    }

    private fun showCleanResult() {
        if (viewModel.scanResult?.candidates?.isEmpty() == true) {
            DeleteSuccessActivity.show(this, removedCount = 0, freedBytes = 0L, allowEmptyResult = true)
            finish()
            return
        }
        selected.clear()
        selected.addAll(CleanupCategory.entries)
        resultBinding.cleanResultBack.setTrackedClickListener(EventUtils.CLICK_CLEANUP_BACK) { finishWithBackInterstitial() }
        resultBinding.cleanupCategories.adapter = CleanupCategoryAdapter(viewModel.scanResult!!, selected) { category ->
            if (!selected.add(category)) selected.remove(category)
            render()
        }
        resultBinding.removeButton.setTrackedClickListener(EventUtils.CLICK_CLEANUP_REMOVE) { deleteSelected() }
        resultBinding.cleanupSettings.setTrackedClickListener(EventUtils.CLICK_CLEANUP_SETTINGS) { showCleanupOptions() }
        render()
    }

    private fun openBigFileCleaner() = startActivity(Intent(this, BigFileCleanerActivity::class.java))

    private fun showCleanupOptions() {
        PopupMenu(this, resultBinding.cleanupSettings).apply {
            menu.add(0, R.string.try_again, 0, R.string.try_again)
            menu.add(0, R.string.big_file_cleaner, 1, R.string.big_file_cleaner)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                menu.add(0, R.string.clear_app_cache, 2, R.string.clear_app_cache)
            }
            menu.add(0, R.string.scan_complete, 3, R.string.scan_complete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.string.try_again -> startScan()
                    R.string.big_file_cleaner -> openBigFileCleaner()
                    R.string.clear_app_cache -> openSystemCacheCleaner()
                    R.string.scan_complete -> viewModel.scanResult?.let { result ->
                        AlertDialog.Builder(this@CleanUpActivity)
                            .setTitle(R.string.scan_complete)
                            .setMessage(getString(R.string.clean_scan_summary, result.stats.scannedFiles, result.stats.scannedDirectories))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
                true
            }
        }.show()
    }

    private fun render() {
        val result = viewModel.scanResult ?: return
        val candidates = result.candidates.filter { it.category in selected }
        val selectedBytes = candidates.sumOf { it.sizeBytes }
        resultBinding.cleanupTotalSize.text = if (selectedBytes == 0L && candidates.isNotEmpty()) {
            getString(R.string.items_found, candidates.size)
        } else DeviceStats.formatBytes(this, selectedBytes)
        resultBinding.cleanupScanWarning.apply {
            visibility = if (result.stats.limitReached || result.stats.inaccessibleDirectories > 0) View.VISIBLE else View.GONE
            text = if (result.stats.limitReached) getString(R.string.scan_incomplete)
            else getString(R.string.scan_restricted_summary, result.stats.inaccessibleDirectories)
        }
        resultBinding.removeButton.isEnabled = candidates.isNotEmpty() && !isDeleting
    }

    private fun deleteSelected() {
        if (isDeleting) return
        val selectedCategories = selected.toSet()
        val candidates = viewModel.scanResult?.candidates?.filter { it.category in selectedCategories }.orEmpty()
        if (candidates.isEmpty()) {
            Toast.makeText(this, R.string.no_junk, Toast.LENGTH_SHORT).show()
            return
        }
        isDeleting = true
        render()
        cleaningLauncher.launch(JunkCleaningActivity.createIntent(this, candidates))
    }

    private fun openSystemCacheCleaner() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
        } else Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
    }

}
