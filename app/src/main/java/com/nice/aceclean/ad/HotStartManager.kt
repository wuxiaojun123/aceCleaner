package com.nice.aceclean.ad

import android.app.Application
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nice.aceclean.ui.main.SplashActivity
import com.nice.aceclean.util.LogUtils


/**
 * Detects the application returning from the background and opens the hot-start ad flow.
 */
object HotStartManager : DefaultLifecycleObserver {

    const val EXTRA_NOTIFICATION_ENTRY = "notification_entry"

    private const val TAG = "HotStartManager"
    private const val HOT_START_THRESHOLD_MS = 300L

    private lateinit var app: Application
    private var backgroundTimestamp = 0L
    private var initialized = false

    @Volatile
    private var hotStartAdActive = false

    @Volatile
    var skipNextHotStart = false

    /** Call before opening a system or external page whose return is not a real hot start. */
    fun markSkipNextHotStart() {
        skipNextHotStart = true
    }

    /** Notification navigation already owns this foreground entry; preserve its destination. */
    fun handleNotificationEntry(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_NOTIFICATION_ENTRY, false)) return
        intent.removeExtra(EXTRA_NOTIFICATION_ENTRY)
        // Clear only the pending background transition, so this cannot skip a later hot start.
        backgroundTimestamp = 0L
        LogUtils.d(tag = TAG, msg = "Notification entry keeps its requested destination")
    }

    fun markHotStartAdFinished() {
        hotStartAdActive = false
        backgroundTimestamp = 0L
        LogUtils.d(tag = TAG, msg = "Hot-start ad is no longer active")
    }

    /** Avoid starting another splash while an existing startup flow is active. */
    fun markSplashAdStarted() {
        hotStartAdActive = true
    }

    fun init(application: Application) {
        if (initialized) return
        initialized = true
        app = application
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        LogUtils.d(tag = TAG, msg = "Registered process lifecycle observer")
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundTimestamp = System.currentTimeMillis()
        LogUtils.d(tag = TAG, msg = "Application entered background")
    }

    override fun onStart(owner: LifecycleOwner) {
        if (backgroundTimestamp == 0L) {
            // A skip request made while the process is cold must not leak into the next session.
            skipNextHotStart = false
            return
        }

        if (skipNextHotStart) {
            skipNextHotStart = false
            backgroundTimestamp = 0L
            LogUtils.d(tag = TAG, msg = "Skipped requested foreground transition")
            return
        }

        val elapsed = System.currentTimeMillis() - backgroundTimestamp
        backgroundTimestamp = 0L
        if (elapsed < HOT_START_THRESHOLD_MS || hotStartAdActive) {
            return
        }

        val intent = Intent(app, SplashActivity::class.java).apply {
            putExtra(SplashActivity.EXTRA_HOT_START, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        hotStartAdActive = true
        try {
            app.startActivity(intent)
        } catch (error: Exception) {
            hotStartAdActive = false
            LogUtils.e(tag = TAG, msg = "Unable to launch hot-start ad", throwable = error)
        }
    }
}
