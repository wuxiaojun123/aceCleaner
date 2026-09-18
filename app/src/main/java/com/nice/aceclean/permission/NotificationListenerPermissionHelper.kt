package com.nice.aceclean.permission

import androidx.fragment.app.FragmentActivity

class NotificationListenerPermissionHelper(
    private val activity: FragmentActivity,
    onGranted: () -> Unit,
) : AutoCloseable {
    private val poller = SettingsPermissionPoller(
        activity = activity,
        isGranted = { SpecialPermissionAccess.isGranted(it, SpecialPermission.NOTIFICATION_LISTENER) },
        onGranted = onGranted,
    )

    fun isGranted(): Boolean =
        SpecialPermissionAccess.isGranted(activity, SpecialPermission.NOTIFICATION_LISTENER)

    fun request(): Boolean = poller.openSettings(
        SpecialPermissionAccess.settingsIntent(activity, SpecialPermission.NOTIFICATION_LISTENER),
    )

    override fun close() = poller.close()
}
