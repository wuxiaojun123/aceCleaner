package com.nice.aceclean.ui.onboarding

import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.nice.aceclean.permission.SettingsPermissionPoller
import com.nice.aceclean.permission.PermissionRequestAnalytics

/** Onboarding keeps its next-page decisions while sharing grant monitoring with feature pages. */
internal class OnboardingSettingsPermission(
    activity: FragmentActivity,
    analytics: PermissionRequestAnalytics,
    isGranted: () -> Boolean,
    private val onFinished: (Boolean) -> Unit,
) : AutoCloseable {
    private val poller = SettingsPermissionPoller(
        activity = activity,
        analytics = analytics,
        isGranted = { isGranted() },
        onGranted = { onFinished(true) },
        onDenied = { onFinished(false) },
        pendingExtra = "onboarding_settings_pending",
    )

    fun restorePendingRequest() = poller.restorePendingRequest()

    fun request(vararg intents: Intent): Boolean {
        val opened = poller.openSettings(*intents)
        if (!opened) onFinished(false)
        return opened
    }

    override fun close() = poller.close()
}
