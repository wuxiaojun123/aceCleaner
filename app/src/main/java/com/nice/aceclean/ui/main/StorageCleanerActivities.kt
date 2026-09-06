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
import androidx.fragment.app.Fragment
import com.nice.aceclean.R
import com.nice.aceclean.permission.StorageAccess
import com.nice.aceclean.ui.base.BaseActivity

enum class StorageCleanerType { SCREENSHOT, BIG_FILE, VIDEO }

abstract class StorageCleanerActivity(
    private val cleanerType: StorageCleanerType,
) : BaseActivity(R.layout.activity_main) {

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasAccess()) showScanner() else showPermissionRequired()
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasAccess()) showScanner() else showPermissionRequired()
    }

    override fun initViews() {
        if (supportFragmentManager.findFragmentById(R.id.main_container) != null) return
        if (hasAccess()) showScanner() else requestAccess()
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

    private fun showScanner() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, MediaScanFragment.newInstance(cleanerType))
            .commit()
    }

    fun showResult() {
        val fragment: Fragment = when (cleanerType) {
            StorageCleanerType.SCREENSHOT -> ScreenshotCleanerFragment()
            StorageCleanerType.BIG_FILE -> BigFileCleanerFragment()
            StorageCleanerType.VIDEO -> VideoCleanerFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, fragment)
            .commit()
    }
}

class ScreenshotCleanerActivity : StorageCleanerActivity(StorageCleanerType.SCREENSHOT)

class BigFileCleanerActivity : StorageCleanerActivity(StorageCleanerType.BIG_FILE)

class VideoCleanerActivity : StorageCleanerActivity(StorageCleanerType.VIDEO)
