package com.nice.aceclean

import android.app.Application
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.ad.HotStartManager

class App : Application(){

    override fun onCreate() {
        super.onCreate()
        AppAdManager.initialize(this)
        HotStartManager.init(this)
    }

}
