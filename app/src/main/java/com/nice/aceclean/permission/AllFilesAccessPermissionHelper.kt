package com.nice.aceclean.permission

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity

class AllFilesAccessPermissionHelper(
    private val activity: FragmentActivity,
    private val onGranted: () -> Unit,
    private val onDenied: () -> Unit,
) : AutoCloseable {
    private val runtimeLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (isGranted()) onGranted() else onDenied()
    }
    private val poller = SettingsPermissionPoller(
        activity = activity,
        isGranted = { StorageAccess.hasBroadAccess(it) },
        onGranted = onGranted,
    )

    fun isGranted(): Boolean = StorageAccess.hasBroadAccess(activity)

    fun request() {
        if (isGranted()) {
            onGranted()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val opened = poller.openSettings(
                StorageAccess.broadAccessSettingsIntent(activity),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            )
            if (!opened) onDenied()
        } else {
            runtimeLauncher.launch(StorageAccess.legacyPermissions())
        }
    }

    fun openAppSettings() {
        if (!poller.openSettings(StorageAccess.appSettingsIntent(activity))) onDenied()
    }

    override fun close() = poller.close()
}
