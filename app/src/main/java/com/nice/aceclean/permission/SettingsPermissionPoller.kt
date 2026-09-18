package com.nice.aceclean.permission

import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** Polls a settings-based permission even while the app's activity is paused behind Settings. */
internal class SettingsPermissionPoller(
    activity: FragmentActivity,
    private val isGranted: (FragmentActivity) -> Boolean,
    private val onGranted: () -> Unit,
) : AutoCloseable {
    private val activityRef = WeakReference(activity)
    private var pollingJob: Job? = null
    private var delivered = false

    fun openSettings(vararg intents: Intent): Boolean {
        val activity = activityRef.get() ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        if (isGranted(activity)) {
            deliver(activity)
            return true
        }
        val opened = intents.any { intent -> runCatching { activity.startActivity(intent) }.isSuccess }
        if (opened) startPolling()
        return opened
    }

    fun startPolling() {
        pollingJob?.cancel()
        delivered = false
        val activity = activityRef.get() ?: return
        pollingJob = activity.lifecycleScope.launch {
            val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val current = activityRef.get()
                if (current == null || current.isFinishing || current.isDestroyed) break
                if (isGranted(current)) {
                    deliver(current)
                    break
                }
                delay(POLL_INTERVAL_MS)
            }
            pollingJob = null
        }
    }

    private fun deliver(activity: FragmentActivity) {
        if (delivered) return
        delivered = true
        pollingJob?.cancel()
        pollingJob = null
        runCatching {
            activity.startActivity(Intent(activity, activity.javaClass).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
        onGranted()
    }

    override fun close() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 400L
        const val POLL_TIMEOUT_MS = 3 * 60 * 1000L
    }
}
