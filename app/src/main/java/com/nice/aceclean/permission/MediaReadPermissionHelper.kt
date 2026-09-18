package com.nice.aceclean.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class MediaReadType { IMAGES, VIDEO }

class MediaReadPermissionHelper(
    private val activity: FragmentActivity,
    private val type: MediaReadType,
    private val onGranted: () -> Unit,
    private val onDenied: () -> Unit,
) : AutoCloseable {
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (isGranted()) onGranted() else onDenied()
    }
    private val settingsPoller = SettingsPermissionPoller(
        activity = activity,
        isGranted = { isGranted() },
        onGranted = onGranted,
    )

    fun isGranted(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            val fullAccess = hasPermission(
                if (type == MediaReadType.VIDEO) Manifest.permission.READ_MEDIA_VIDEO
                else Manifest.permission.READ_MEDIA_IMAGES,
            )
            val selectedAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            fullAccess || selectedAccess
        }
        else -> hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun request() {
        if (isGranted()) {
            onGranted()
            return
        }
        launcher.launch(buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(if (type == MediaReadType.VIDEO) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray())
    }

    fun openAppSettings() {
        if (!settingsPoller.openSettings(StorageAccess.appSettingsIntent(activity))) onDenied()
    }

    override fun close() = settingsPoller.close()

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
}
