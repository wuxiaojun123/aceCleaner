package com.nice.aceclean.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.nice.aceclean.R
import com.nice.aceclean.permission.StorageAccess
import com.nice.aceclean.ui.base.BaseActivity

class CleanUpActivity : BaseActivity(R.layout.activity_main) {

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (StorageAccess.hasBroadAccess(this)) showScanner() else showPermissionRequired()
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (StorageAccess.hasBroadAccess(this)) showScanner() else showPermissionRequired()
    }

    override fun initViews() {
        if (supportFragmentManager.findFragmentById(R.id.main_container) != null) return
        if (StorageAccess.hasBroadAccess(this)) showScanner() else requestStorageAccess()
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                settingsLauncher.launch(StorageAccess.broadAccessSettingsIntent(this))
            } catch (_: ActivityNotFoundException) {
                settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            permissionLauncher.launch(StorageAccess.legacyPermissions())
        }
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

    private fun showScanner() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, CleanScanFragment())
            .commit()
    }

    fun restartScan() = showScanner()

    fun showCleanResult() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, CleanResultFragment())
            .commit()
    }
}

abstract class FeatureActivity(
    private val featureType: FeatureType,
) : BaseActivity(R.layout.activity_main) {

    override fun initViews() {
        if (supportFragmentManager.findFragmentById(R.id.main_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, PermissionGateFragment.newInstance(featureType))
                .commit()
        }
    }

    fun showFeatureScreen(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, fragment)
            .commit()
    }
}

class NetworkTrafficActivity : FeatureActivity(FeatureType.NETWORK_TRAFFIC)

class NotificationCleanerActivity : FeatureActivity(FeatureType.NOTIFICATION_CLEANER)

class AppManagerActivity : FeatureActivity(FeatureType.APP_MANAGER)
