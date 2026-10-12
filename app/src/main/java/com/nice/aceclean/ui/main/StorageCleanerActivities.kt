package com.nice.aceclean.ui.main

import androidx.lifecycle.lifecycleScope
import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding
import com.nice.aceclean.R
import com.nice.aceclean.databinding.FragmentMediaScanBinding
import com.nice.aceclean.permission.AllFilesAccessPermissionHelper
import com.nice.aceclean.permission.MediaReadPermissionHelper
import com.nice.aceclean.permission.MediaReadType
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.ui.dialog.IosActionSheetDialog
import com.nice.aceclean.util.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StorageCleanerType { SCREENSHOT, BIG_FILE, VIDEO }

abstract class StorageCleanerActivity<B : ViewBinding>(
    private val cleanerType: StorageCleanerType,
    private val inflateResult: (LayoutInflater) -> B,
    private val resultLayout: Int,
) : BaseActivity<FragmentMediaScanBinding>({ FragmentMediaScanBinding.inflate(it) }, R.layout.fragment_media_scan) {

    protected lateinit var resultBinding: B
        private set
    protected val isResultShown get() = ::resultBinding.isInitialized

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
        IosActionSheetDialog.show(
            activity = this,
            title = getString(R.string.storage_permission_required),
            message = getString(R.string.storage_permission_explanation),
            actions = listOf(
                IosActionSheetDialog.Action(getString(R.string.try_again), onClick = ::requestAccess),
                IosActionSheetDialog.Action(
                    getString(R.string.open_app_settings),
                    onClick = ::openPermissionSettings,
                ),
            ),
            onCancel = ::finish,
        )
    }

    private fun startScan() {
        val scanBinding = setActivityContent(FragmentMediaScanBinding.inflate(layoutInflater), R.layout.fragment_media_scan)
        scanBinding.mediaScanAnimation.setAnimation(when (cleanerType) {
            StorageCleanerType.SCREENSHOT -> R.raw.picture_clean_scan_anim
            StorageCleanerType.BIG_FILE -> R.raw.big_file_scan_anim
            StorageCleanerType.VIDEO -> R.raw.video_clean_scan_anim
        })
        scanBinding.mediaScanLabel.setText(when (cleanerType) {
            StorageCleanerType.SCREENSHOT -> R.string.scanning_picture
            StorageCleanerType.BIG_FILE -> R.string.scanning_large_files
            StorageCleanerType.VIDEO -> R.string.scanning_video
        })
        val percentage = scanBinding.mediaScanPercentage
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
            resultBinding = setActivityContent(inflateResult(layoutInflater), resultLayout)
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
