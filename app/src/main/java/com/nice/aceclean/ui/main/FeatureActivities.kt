package com.nice.aceclean.ui.main

import androidx.fragment.app.Fragment
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseActivity

class CleanUpActivity : BaseActivity(R.layout.activity_main) {

    override fun initViews() {
        if (supportFragmentManager.findFragmentById(R.id.main_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, CleanScanFragment())
                .commit()
        }
    }

    fun showCleanResult() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, CleanResultFragment())
            .commit()
    }
}

abstract class FeatureActivity(
    private val featureType: FeatureType,
) : BaseActivity(R.layout.activity_main) {

    override fun initViews() {
        if (supportFragmentManager.findFragmentById(R.id.main_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, PermissionGateFragment.newInstance(featureType))
                .commit()
        }
    }

    fun showFeatureScreen(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, fragment)
            .commit()
    }
}

class NetworkTrafficActivity : FeatureActivity(FeatureType.NETWORK_TRAFFIC)

class NotificationCleanerActivity : FeatureActivity(FeatureType.NOTIFICATION_CLEANER)

class AppManagerActivity : FeatureActivity(FeatureType.APP_MANAGER)
