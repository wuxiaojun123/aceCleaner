package com.nice.aceclean.ui.main

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.nice.aceclean.R
import com.nice.aceclean.service.StickyNotificationService
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
        startStickyNotification()
    }

    private fun startStickyNotification() {
        StickyNotificationService.start(this)
        requestQuickSettingsTileIfNeeded()
    }

    /** Ask the system to add the Quick Settings tile once, without the app's bottom sheet. */
    private fun requestQuickSettingsTileIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val preferences = getSharedPreferences(QUICK_TILE_PREFS, MODE_PRIVATE)
        if (preferences.getBoolean(KEY_QUICK_TILE_REQUESTED, false)) return

        window.decorView.post {
            if (isFinishing || isDestroyed || preferences.getBoolean(KEY_QUICK_TILE_REQUESTED, false)) {
                return@post
            }
            // QuickCleanBottomDialogFragment.show(supportFragmentManager)
            preferences.edit().putBoolean(KEY_QUICK_TILE_REQUESTED, true).apply()
            requestQuickSettingsTile()
        }
    }

    private fun requestQuickSettingsTile() {
        val manager = getSystemService(StatusBarManager::class.java) ?: return
        manager.requestAddTileService(
            ComponentName(this, QuickCleanTileService::class.java),
            getString(R.string.quick_clean_tile_label),
            Icon.createWithResource(this, R.drawable.ic_sticky_notification),
            mainExecutor,
        ) { _ ->
            // The platform owns the result UI. The tile works immediately when added.
        }
    }

    fun openCleanUp() = startActivity(Intent(this, CleanUpActivity::class.java))

    fun openNetworkTraffic() = startActivity(Intent(this, NetworkTrafficActivity::class.java))

    fun openNotificationCleaner() = startActivity(Intent(this, NotificationCleanerActivity::class.java))

    fun openAppManager() = startActivity(Intent(this, AppManagerActivity::class.java))

    fun openScreenshotCleaner() = startActivity(Intent(this, ScreenshotCleanerActivity::class.java))

    fun openBigFileCleaner() = startActivity(Intent(this, BigFileCleanerActivity::class.java))

    fun openVideoCleaner() = startActivity(Intent(this, VideoCleanerActivity::class.java))

    fun openDuplicatePhotoCleaner() = startActivity(Intent(this, SimilarPhotoCleanerActivity::class.java))

    fun openRamStatus() = startActivity(Intent(this, RamStatusActivity::class.java))

    fun openDeviceScan() = startActivity(Intent(this, DeviceReportActivity::class.java))

    fun openBatteryInfo() = startActivity(Intent(this, BatteryInfoActivity::class.java))

    fun openNetworkTest() = startActivity(Intent(this, NetworkTestActivity::class.java))

    fun openLanguage() = languageLauncher.launch(Intent(this, LanguageActivity::class.java))

    private companion object {
        const val QUICK_TILE_PREFS = "quick_settings_tile"
        const val KEY_QUICK_TILE_REQUESTED = "system_tile_requested"
    }
}
