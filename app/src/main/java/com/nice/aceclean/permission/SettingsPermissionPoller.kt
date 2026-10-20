package com.nice.aceclean.permission

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.ad.HotStartManager
import com.nice.aceclean.util.LogUtils
import com.nice.aceclean.ui.main.DefaultLauncherTipsActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Checks grants behind Settings and brings the permission flow forward as soon as access is enabled. */
internal class SettingsPermissionPoller(
    private val activity: FragmentActivity,
    private val analytics: PermissionRequestAnalytics,
    private val isGranted: (FragmentActivity) -> Boolean,
    private val onGranted: () -> Unit,
    private val onDenied: () -> Unit = {},
    private val pendingExtra: String? = null,
) : AutoCloseable {
    private var pollingJob: Job? = null
    private var tipsJob: Job? = null
    private var ownsStorageTips = false
    private var pending = false
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Settings result codes do not describe permission state. Also handles returns after polling times out.
        complete(bringToFront = false)
    }

    private val grantObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // Catch grants on return before page onResume code can finish the permission Activity.
            // The analytics state also survives a destroyed/recreated Settings caller.
            if (analytics.isPending() && isGranted(activity)) {
                pending = true
                complete(bringToFront = false)
            }
        }
    }

    init {
        activity.lifecycle.addObserver(grantObserver)
    }

    fun restorePendingRequest() {
        pending = pendingExtra?.let { activity.intent.getBooleanExtra(it, false) } == true
        if (pending) startPolling()
    }

    fun openSettings(vararg intents: Intent): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        if (pending) return true
        pending = true
        if (isGranted(activity)) {
            complete(bringToFront = false)
            return true
        }
        pendingExtra?.let { activity.intent.putExtra(it, true) }
        analytics.requested()
        val previousSkip = HotStartManager.skipNextHotStart
        HotStartManager.markSkipNextHotStart()
        val opened = intents.any { intent ->
            runCatching { launcher.launch(intent) }.onFailure {
                LogUtils.w(tag = TAG, msg = "Unable to open permission settings: ${intent.action}", throwable = it)
            }.isSuccess
        }
        if (opened) {
            startPolling()
            if (intents.any { it.action == Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION ||
                    it.action == Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION }) {
                scheduleStorageTips()
            }
        } else {
            analytics.result(false)
            pending = false
            pendingExtra?.let(activity.intent::removeExtra)
            HotStartManager.skipNextHotStart = previousSkip
        }
        return opened
    }

    private fun scheduleStorageTips() {
        tipsJob?.cancel()
        tipsJob = activity.lifecycleScope.launch {
            delay(350L)
            if (!pending || isGranted(activity) || activity.isFinishing || activity.isDestroyed ||
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            runCatching { DefaultLauncherTipsActivity.start(activity) }
                .onSuccess { ownsStorageTips = true }
                .onFailure { LogUtils.w(tag = TAG, msg = "Unable to show storage tips", throwable = it) }
        }
    }

    private fun dismissStorageTips() {
        tipsJob?.cancel()
        tipsJob = null
        if (ownsStorageTips) DefaultLauncherTipsActivity.dismiss()
        ownsStorageTips = false
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = activity.lifecycleScope.launch {
            val deadline = SystemClock.elapsedRealtime() + POLL_TIMEOUT_MS
            while (isActive && pending && SystemClock.elapsedRealtime() < deadline) {
                if (activity.isFinishing || activity.isDestroyed) break
                if (isGranted(activity)) {
                    complete(bringToFront = true)
                    return@launch
                }
                delay(POLL_INTERVAL_MS)
            }
            // Keep the request pending: the Settings result still checks access on manual return.
            pollingJob = null
        }
    }

    private fun complete(bringToFront: Boolean) {
        if (!pending || activity.isFinishing || activity.isDestroyed) return
        val granted = isGranted(activity)
        analytics.result(granted)
        pending = false
        dismissStorageTips()
        pendingExtra?.let(activity.intent::removeExtra)
        pollingJob?.cancel()
        pollingJob = null
        if (granted) {
            if (bringToFront && !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                HotStartManager.markSkipNextHotStart()
                runCatching {
                    activity.startActivity(Intent(activity, activity.javaClass).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                }.onFailure {
                    LogUtils.w(tag = TAG, msg = "Unable to bring permission flow to foreground", throwable = it)
                }
            }
            onGranted()
        } else {
            onDenied()
        }
    }

    override fun close() {
        activity.lifecycle.removeObserver(grantObserver)
        pending = false
        dismissStorageTips()
        pollingJob?.cancel()
        pollingJob = null
    }

    private companion object {
        const val TAG = "SettingsPermission"
        const val POLL_INTERVAL_MS = 400L
        const val POLL_TIMEOUT_MS = 3 * 60 * 1000L
    }
}
