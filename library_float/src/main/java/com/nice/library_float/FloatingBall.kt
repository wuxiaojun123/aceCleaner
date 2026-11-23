package com.nice.library_float

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

enum class FloatRoute { QUICK_SCAN, PHOTOS, NOTIFY, WIDGET_PROCESS_MANAGER }

interface FloatingBallHost {
    fun openRoute(context: Context, route: FloatRoute)
    fun interceptedToday(context: Context): Int = 0
    fun onBeforeExternalEntry() {}
    fun reportEvent(event: String, params: Map<String, String>) {}
    fun log(level: String, tag: String, message: String, error: Throwable?) {}
    fun preloadNetworkResult(context: Context) {}
    fun bindNetworkResultAd(activity: Activity, container: ViewGroup) {}
}

object FloatingBall : DefaultLifecycleObserver {
    internal lateinit var context: Context
    internal lateinit var host: FloatingBallHost
    internal var debugTiming = false
    private var initialized = false

    fun initialize(application: Application, host: FloatingBallHost, debugTiming: Boolean = false) {
        check(!initialized) { "FloatingBall is already initialized" }
        this.context = application
        this.host = host
        this.debugTiming = debugTiming
        initialized = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun setEnabled(enabled: Boolean) {
        check(initialized)
        if (enabled) PerformanceDashboardPreferenceStore.markOpenedByUser(context)
        else {
            PerformanceDashboardPreferenceStore.markClosedByUser(context)
            FloatBallCoordinator.onUserDisabled()
        }
    }

    fun isEnabled(): Boolean = initialized && !PerformanceDashboardPreferenceStore.isClosedByUser(context)

    override fun onStart(owner: LifecycleOwner) = FloatBallCoordinator.onAppForeground()
    override fun onStop(owner: LifecycleOwner) = FloatBallCoordinator.onAppBackground(context)
}

internal object LogUtils {
    fun d(msg: String, tag: String) = FloatingBall.host.log("debug", tag, msg, null)
    fun w(msg: String, tag: String, error: Throwable? = null) = FloatingBall.host.log("warning", tag, msg, error)
    fun e(msg: String, tag: String, error: Throwable? = null) = FloatingBall.host.log("error", tag, msg, error)
}
