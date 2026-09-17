package com.nice.aceclean.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.nice.aceclean.R
import com.nice.aceclean.service.StickyNotificationService
import com.nice.aceclean.ui.LanguageActivity
import com.nice.aceclean.ui.base.BaseActivity

class MainActivity : BaseActivity(R.layout.activity_main) {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        StickyNotificationService.start(this)
    }

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
        startStickyNotification()
    }

    private fun startStickyNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            StickyNotificationService.start(this)
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

    fun openRamStatus() = startActivity(Intent(this, RamStatusActivity::class.java))

    fun openDeviceScan() = startActivity(Intent(this, DeviceReportActivity::class.java))

    fun openBatteryInfo() = startActivity(Intent(this, BatteryInfoActivity::class.java))

    fun openNetworkTest() = startActivity(Intent(this, NetworkTestActivity::class.java))

    fun openLanguage() = languageLauncher.launch(Intent(this, LanguageActivity::class.java))
}
