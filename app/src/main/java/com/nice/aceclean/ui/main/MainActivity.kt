package com.nice.aceclean.ui.main

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import com.nice.aceclean.R
import com.nice.aceclean.ui.LanguageActivity
import com.nice.aceclean.ui.base.BaseActivity

class MainActivity : BaseActivity(R.layout.activity_main) {

    private val languageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK &&
            result.data?.getBooleanExtra(LanguageActivity.EXTRA_LANGUAGE_CHANGED, false) == true
        ) {
            recreate()
        }
    }

    override fun initViews() {
        if (supportFragmentManager.findFragmentById(R.id.main_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, HomeFragment())
                .commit()
        }
    }

    fun openCleanUp() = startActivity(Intent(this, CleanUpActivity::class.java))

    fun openNetworkTraffic() = startActivity(Intent(this, NetworkTrafficActivity::class.java))

    fun openNotificationCleaner() = startActivity(Intent(this, NotificationCleanerActivity::class.java))

    fun openAppManager() = startActivity(Intent(this, AppManagerActivity::class.java))

    fun openScreenshotCleaner() = startActivity(Intent(this, ScreenshotCleanerActivity::class.java))

    fun openBigFileCleaner() = startActivity(Intent(this, BigFileCleanerActivity::class.java))

    fun openVideoCleaner() = startActivity(Intent(this, VideoCleanerActivity::class.java))

    fun openDuplicatePhotoCleaner() = startActivity(Intent(this, DuplicatePhotoCleanerActivity::class.java))

    fun openLanguage() = languageLauncher.launch(Intent(this, LanguageActivity::class.java))
}
