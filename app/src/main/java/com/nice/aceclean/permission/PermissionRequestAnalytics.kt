package com.nice.aceclean.permission

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.nice.aceclean.util.EventUtils

/** Counts actual requests and successful grants, rather than clicks or repeated permission checks. */
internal class PermissionRequestAnalytics(
    activity: FragmentActivity,
    private val permission: Type,
) {
    enum class Type(val requestEvent: String, val grantedEvent: String) {
        STORAGE("permission_request_storage", "permission_granted_storage"),
        IMAGES("permission_request_images", "permission_granted_images"),
        VIDEO("permission_request_video", "permission_granted_video"),
        USAGE_ACCESS("permission_request_usage_access", "permission_granted_usage_access"),
        NOTIFICATION_LISTENER("permission_request_notification_listener", "permission_granted_notification_listener"),
        POST_NOTIFICATIONS("permission_request_post_notifications", "permission_granted_post_notifications"),
        OVERLAY("permission_request_overlay", "permission_granted_overlay"),
    }

    // Keep an in-flight request through recreation and process restoration.
    private val state by lazy {
        val key = "permission_analytics_${permission.name}"
        val restored = activity.savedStateRegistry.consumeRestoredStateForKey(key) ?: Bundle()
        activity.savedStateRegistry.registerSavedStateProvider(key) { restored }
        restored
    }

    fun requested() {
        state.putBoolean("pending", true)
        EventUtils.track(permission.requestEvent)
    }

    fun isPending(): Boolean = state.getBoolean("pending")

    fun result(granted: Boolean) {
        if (!state.getBoolean("pending")) return
        state.putBoolean("pending", false)
        if (granted) EventUtils.track(permission.grantedEvent)
    }
}
