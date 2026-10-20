package com.nice.aceclean.notification

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.nice.aceclean.ad.HotStartManager
import com.nice.aceclean.ui.main.NotificationDestination
import com.nice.aceclean.ui.main.SplashActivity
import com.nice.aceclean.util.LogUtils
import com.nice.library_push.api.PushLibrary
import com.nice.library_push.api.PushPermissions
import com.nice.library_push.api.PushRouter
import com.nice.library_push.api.PushTracker
import com.nice.library_push.backend.PushConfigSource
import com.nice.library_push.business.news.PushNewsActivity
import com.nice.library_push.display.PushClickActivity

/** Owns the app's policy and destinations; data remains supplied by the selected backend. */
object BusinessPushManager {
    private var initialized = false
    private var permissions: List<Boolean>? = null

    fun initialize(application: Application) {
        if (initialized) return
        initialized = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is PushClickActivity || activity is PushNewsActivity) {
                    // The notification owns this foreground entry, including process recreation.
                    activity.intent.putExtra(HotStartManager.EXTRA_NOTIFICATION_ENTRY, true)
                    HotStartManager.handleNotificationEntry(activity.intent)
                }
            }
            override fun onActivityResumed(activity: Activity) {
                val current = listOf(PushPermissions.canPostNotifications(activity), PushPermissions.canShowOverlay(activity),
                    PushPermissions.canReadScreenshots(activity), PushPermissions.canObserveFiles(activity))
                if (current != permissions) {
                    permissions = current
                    PushLibrary.refreshPermissions()
                }
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        PushLibrary.initialize(
            application = application,
            source = PushConfigSource { application.assets.open("push_config.json").bufferedReader().use { it.readText() } },
            router = PushRouter { context, click ->
                if (click.type == "news") PushNewsActivity.open(context, click.target)
                else context.startActivity(SplashActivity.notificationIntent(context, NotificationDestination.HOME))
            },
            tracker = PushTracker { event, attributes ->
                LogUtils.d(tag = "BusinessPush", msg = "$event $attributes")
            },
        )
    }
}
