package com.nice.aceclean.analytics

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.cater.bi.e.Eve
import com.nice.aceclean.ui.base.BaseActivity

/** Reports each business Activity creation, including recreation, without tracking SDK screens. */
internal object ActivityPageTracker : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is BaseActivity<*>) {
            Eve.send("show_${activity.javaClass.simpleName}", emptyMap())
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
