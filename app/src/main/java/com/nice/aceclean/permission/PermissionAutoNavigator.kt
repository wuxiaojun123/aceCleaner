package com.nice.aceclean.permission

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Special permissions that are granted from a system settings screen instead of a runtime dialog.
 */
enum class SpecialPermission {
    USAGE_ACCESS,
    NOTIFICATION_LISTENER,
}

object SpecialPermissionAccess {
    fun isGranted(context: Context, permission: SpecialPermission): Boolean = when (permission) {
        SpecialPermission.USAGE_ACCESS -> {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                context.applicationInfo.uid,
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }

        SpecialPermission.NOTIFICATION_LISTENER ->
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun settingsIntent(context: Context, permission: SpecialPermission): Intent = when (permission) {
        SpecialPermission.USAGE_ACCESS -> Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

        SpecialPermission.NOTIFICATION_LISTENER ->
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}

/**
 * Lifecycle-aware permission checker for settings-based permissions.
 *
 * It checks immediately when the screen resumes, then polls while the screen remains visible.
 * Once permission is granted, [onGranted] is delivered exactly once. This makes the class safe
 * to reuse in activities and fragments without leaking callbacks after their lifecycle stops.
 */
class PermissionAutoNavigator(
    lifecycleOwner: LifecycleOwner,
    private val isGranted: () -> Boolean,
    private val onGranted: () -> Unit,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
) : DefaultLifecycleObserver, AutoCloseable {

    private val lifecycle = lifecycleOwner.lifecycle
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var delivered = false

    private val poll = object : Runnable {
        override fun run() {
            if (!resumed || delivered) return
            if (isGranted()) {
                delivered = true
                handler.removeCallbacks(this)
                onGranted()
            } else {
                handler.postDelayed(this, pollIntervalMillis)
            }
        }
    }

    init {
        require(pollIntervalMillis > 0) { "pollIntervalMillis must be greater than zero" }
        lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        resumed = true
        checkNow()
    }

    override fun onPause(owner: LifecycleOwner) {
        resumed = false
        handler.removeCallbacks(poll)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        close()
    }

    fun checkNow() {
        handler.removeCallbacks(poll)
        poll.run()
    }

    override fun close() {
        resumed = false
        handler.removeCallbacks(poll)
        lifecycle.removeObserver(this)
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 300L
    }
}
