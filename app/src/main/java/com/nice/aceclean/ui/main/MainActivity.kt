package com.nice.aceclean.ui.main

import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseActivity

class MainActivity : BaseActivity(R.layout.activity_main) {

    override fun initViews() {
        if (supportFragmentManager.findFragmentById(R.id.main_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, HomeFragment())
                .commit()
        }
    }

    fun openCleanUp() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, CleanScanFragment())
            .addToBackStack(null)
            .commit()
    }

    fun showCleanResult() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, CleanResultFragment())
            .commit()
    }
}
