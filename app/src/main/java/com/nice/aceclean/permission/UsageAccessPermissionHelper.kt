package com.nice.aceclean.permission

import androidx.fragment.app.FragmentActivity
import android.widget.Toast
import com.nice.aceclean.R

class UsageAccessPermissionHelper(
    private val activity: FragmentActivity,
    onGranted: () -> Unit,
) : AutoCloseable {
    private val poller = SettingsPermissionPoller(
        activity = activity,
        isGranted = { SpecialPermissionAccess.isGranted(it, SpecialPermission.USAGE_ACCESS) },
        onGranted = onGranted,
    )

    fun isGranted(): Boolean = SpecialPermissionAccess.isGranted(activity, SpecialPermission.USAGE_ACCESS)

    fun request(): Boolean {
        val intents = SpecialPermissionAccess.usageAccessSettingsIntents(activity)
        val hasDetailsPage = intents.firstOrNull()?.component?.className
            ?.contains("AppUsageAccess", ignoreCase = true) == true
        if (!hasDetailsPage) {
            val appName = activity.applicationInfo.loadLabel(activity.packageManager)
            Toast.makeText(
                activity,
                activity.getString(R.string.usage_access_select_app, appName),
                Toast.LENGTH_LONG,
            ).show()
        }
        return poller.openSettings(*intents)
    }

    override fun close() = poller.close()
}
