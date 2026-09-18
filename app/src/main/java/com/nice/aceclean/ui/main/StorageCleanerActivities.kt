package com.nice.aceclean.ui.main

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.nice.aceclean.R
import com.nice.aceclean.permission.AllFilesAccessPermissionHelper
import com.nice.aceclean.permission.MediaReadPermissionHelper
import com.nice.aceclean.permission.MediaReadType
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StorageCleanerType { SCREENSHOT, BIG_FILE, VIDEO }

abstract class StorageCleanerActivity(
    private val cleanerType: StorageCleanerType,
    private val resultLayout: Int,
) : BaseActivity(R.layout.fragment_media_scan) {

    private val allFilesPermission = AllFilesAccessPermissionHelper(
        activity = this,
        onGranted = ::startScan,
        onDenied = ::showPermissionRequired,
    )
    private val mediaPermission = MediaReadPermissionHelper(
        activity = this,
        type = if (cleanerType == StorageCleanerType.VIDEO) MediaReadType.VIDEO else MediaReadType.IMAGES,
        onGranted = ::startScan,
        onDenied = ::showPermissionRequired,
    )

    override fun initViews() {
        if (hasAccess()) startScan() else requestAccess()
    }

    private fun hasAccess(): Boolean = permissionHelperIsGranted()

    private fun requestAccess() {
        if (cleanerType == StorageCleanerType.BIG_FILE) allFilesPermission.request() else mediaPermission.request()
    }

    private fun permissionHelperIsGranted(): Boolean =
        if (cleanerType == StorageCleanerType.BIG_FILE) allFilesPermission.isGranted() else mediaPermission.isGranted()

    private fun openPermissionSettings() {
        if (cleanerType == StorageCleanerType.BIG_FILE) allFilesPermission.openAppSettings()
        else mediaPermission.openAppSettings()
    }

    private fun showPermissionRequired() {
        AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_required)
            .setMessage(R.string.storage_permission_explanation)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setNeutralButton(R.string.open_app_settings) { _, _ -> openPermissionSettings() }
            .setPositiveButton(R.string.try_again) { _, _ -> requestAccess() }
            .show()
    }

    private fun startScan() {
        setActivityContent(R.layout.fragment_media_scan)
        findViewById<LottieAnimationView>(R.id.media_scan_animation).setAnimation(when (cleanerType) {
            StorageCleanerType.SCREENSHOT -> R.raw.picture_clean_scan_anim
            StorageCleanerType.BIG_FILE -> R.raw.big_file_scan_anim
            StorageCleanerType.VIDEO -> R.raw.video_clean_scan_anim
        })
        findViewById<android.widget.TextView>(R.id.media_scan_label).setText(when (cleanerType) {
            StorageCleanerType.SCREENSHOT -> R.string.scanning_picture
            StorageCleanerType.BIG_FILE -> R.string.scanning_large_files
            StorageCleanerType.VIDEO -> R.string.scanning_video
        })
        val percentage = findViewById<android.widget.TextView>(R.id.media_scan_percentage)
        percentage.text = getString(R.string.scan_percentage, 0)
        lifecycleScope.launch {
            percentage.text = getString(R.string.scan_percentage, 10)
            withContext(Dispatchers.IO) {
                when (cleanerType) {
                    StorageCleanerType.SCREENSHOT -> MediaStoreRepository.pictures(this@StorageCleanerActivity)
                    StorageCleanerType.BIG_FILE -> MediaStoreRepository.largeFiles(this@StorageCleanerActivity)
                    StorageCleanerType.VIDEO -> MediaStoreRepository.videos(this@StorageCleanerActivity)
                }
            }
            percentage.text = getString(R.string.scan_percentage, 100)
            setActivityContent(resultLayout)
            initResultViews()
        }
    }

    protected abstract fun initResultViews()

    override fun onDestroy() {
        allFilesPermission.close()
        mediaPermission.close()
        super.onDestroy()
    }
}
