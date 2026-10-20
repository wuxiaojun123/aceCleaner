package com.nice.aceclean

import android.app.Application
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.ad.HotStartManager
import com.nice.aceclean.analytics.ActivityPageTracker
import com.nice.aceclean.notification.BusinessPushManager

class App : Application(){

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(ActivityPageTracker)
        AppAdManager.initialize(this)
        HotStartManager.init(this)
        BusinessPushManager.initialize(this)
    }

}
