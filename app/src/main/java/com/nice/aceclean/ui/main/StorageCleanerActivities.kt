package com.nice.aceclean.ui.main

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.nice.aceclean.R
import com.nice.aceclean.permission.StorageAccess
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

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasAccess()) startScan() else showPermissionRequired()
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasAccess()) startScan() else showPermissionRequired()
    }

    override fun initViews() {
        if (hasAccess()) startScan() else requestAccess()
    }

    private fun hasAccess(): Boolean = when {
        cleanerType == StorageCleanerType.BIG_FILE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            val fullAccess = ContextCompat.checkSelfPermission(
                this,
                if (cleanerType == StorageCleanerType.VIDEO) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES,
            ) == PackageManager.PERMISSION_GRANTED
            val selectedAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            fullAccess || selectedAccess
        }
        else -> ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAccess() {
        if (cleanerType == StorageCleanerType.BIG_FILE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            try {
                settingsLauncher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            return
        }
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(if (cleanerType == StorageCleanerType.VIDEO) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && cleanerType == StorageCleanerType.BIG_FILE) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }.toTypedArray()
        permissionLauncher.launch(permissions)
    }

    private fun showPermissionRequired() {
        AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_required)
            .setMessage(R.string.storage_permission_explanation)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setNeutralButton(R.string.open_app_settings) { _, _ -> settingsLauncher.launch(StorageAccess.appSettingsIntent(this)) }
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
}
