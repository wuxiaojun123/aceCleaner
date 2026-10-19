package com.nice.aceclean.ui.main

import android.os.Build
import android.content.Intent
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
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
        categoryViews().forEach { (category, row, check, size) -> bindCategory(row, size, check, category) }
        resultBinding.cleanupMoreRow.setTrackedClickListener(EventUtils.CLICK_CLEANUP_MORE) { openBigFileCleaner() }
        resultBinding.removeButton.setTrackedClickListener(EventUtils.CLICK_CLEANUP_REMOVE) { deleteSelected() }
        resultBinding.cleanupRescan.setTrackedClickListener(EventUtils.CLICK_CLEANUP_RESCAN) { startScan() }
        resultBinding.cleanupFindLargeFiles.setTrackedClickListener(EventUtils.CLICK_CLEANUP_LARGE_FILES) { openBigFileCleaner() }
        resultBinding.cleanupSystemCache.apply {
            visibility = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) View.VISIBLE else View.GONE
            setTrackedClickListener(EventUtils.CLICK_CLEANUP_SYSTEM_CACHE) { openSystemCacheCleaner() }
        }
        render()
    }

    private fun openBigFileCleaner() = startActivity(Intent(this, BigFileCleanerActivity::class.java))

    private fun bindCategory(row: View, size: TextView, check: ImageView, category: CleanupCategory) {
        row.setTrackedClickListener(EventUtils.CLICK_CLEANUP_CATEGORY) {
            if (!selected.add(category)) selected.remove(category)
            render()
        }
        size.text = DeviceStats.formatBytes(this, viewModel.scanResult?.size(category) ?: 0L)
        check.isSelected = category in selected
    }

    private fun render() {
        val result = viewModel.scanResult ?: return
        val isEmpty = result.candidates.isEmpty()
        resultBinding.cleanupTotalSize.apply {
            visibility = if (isEmpty) View.GONE else View.VISIBLE
            text = if (result.totalBytes == 0L && result.candidates.isNotEmpty()) {
                getString(R.string.items_found, result.candidates.size)
            } else DeviceStats.formatBytes(this@CleanUpActivity, result.totalBytes)
        }
        resultBinding.cleanupRemovableLabel.visibility = if (isEmpty) View.GONE else View.VISIBLE
        resultBinding.cleanupEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        resultBinding.removeButton.visibility = if (isEmpty) View.GONE else View.VISIBLE
        resultBinding.cleanupScanDetails.text = getString(
            R.string.clean_scan_summary, result.stats.scannedFiles, result.stats.scannedDirectories,
        )
        resultBinding.cleanupScanWarning.apply {
            visibility = if (result.stats.limitReached || result.stats.inaccessibleDirectories > 0) View.VISIBLE else View.GONE
            text = if (result.stats.limitReached) getString(R.string.scan_incomplete)
            else getString(R.string.scan_restricted_summary, result.stats.inaccessibleDirectories)
        }
        categoryViews().forEach { (category, row, check, size) ->
            row.visibility = if (isEmpty) View.GONE else View.VISIBLE
            check.alpha = if (category in selected) 1f else 0.25f
            val candidates = result.candidates(category)
            size.text = if (candidates.isNotEmpty() && result.size(category) == 0L) {
                getString(R.string.items_found, candidates.size)
            } else DeviceStats.formatBytes(this, result.size(category))
        }
        resultBinding.cleanupMoreRow.visibility = if (isEmpty) View.GONE else View.VISIBLE
        val selectedBytes = result.candidates.filter { it.category in selected }.sumOf { it.sizeBytes }
        resultBinding.removeButton.text = getString(
            R.string.remove_selected_size, DeviceStats.formatBytes(this, selectedBytes),
        )
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
        cleaningLauncher.launch(JunkCleaningActivity.createIntent(this, candidates))
    }

    private fun openSystemCacheCleaner() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
        } else Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
    }

    private data class CategoryViews(val category: CleanupCategory, val row: View, val check: ImageView, val size: TextView)

    private fun categoryViews() = listOf(
        CategoryViews(CleanupCategory.APP_CACHE, resultBinding.cleanupCacheRow, resultBinding.cleanupCacheCheck, resultBinding.cleanupCacheSize),
        CategoryViews(CleanupCategory.AD_FILES, resultBinding.cleanupAdRow, resultBinding.cleanupAdCheck, resultBinding.cleanupAdSize),
        CategoryViews(CleanupCategory.TEMP_FILES, resultBinding.cleanupTempRow, resultBinding.cleanupTempCheck, resultBinding.cleanupTempSize),
        CategoryViews(CleanupCategory.APK_FILES, resultBinding.cleanupApkRow, resultBinding.cleanupApkCheck, resultBinding.cleanupApkSize),
        CategoryViews(CleanupCategory.RESIDUAL_FILES, resultBinding.cleanupResidualRow, resultBinding.cleanupResidualCheck, resultBinding.cleanupResidualSize),
        CategoryViews(CleanupCategory.EMPTY_FILES, resultBinding.cleanupEmptyRow, resultBinding.cleanupEmptyCheck, resultBinding.cleanupEmptySize),
    )

    override fun onDestroy() {
        super.onDestroy()
    }
}
