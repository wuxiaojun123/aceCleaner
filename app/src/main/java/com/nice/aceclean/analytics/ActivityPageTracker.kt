package com.nice.aceclean.analytics

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.EventUtils

/** Reports each business Activity creation, including recreation, without tracking SDK screens. */
internal object ActivityPageTracker : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is BaseActivity<*>) {
            EventUtils.track(EventUtils.activityShowEvent(activity.javaClass))
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
