package com.nice.aceclean.ui.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.nice.aceclean.R
import com.nice.aceclean.cleanup.CleanupScanResult
import com.nice.aceclean.permission.SpecialPermission
import com.nice.aceclean.permission.SpecialPermissionAccess
import com.nice.aceclean.permission.StorageAccess
import com.nice.aceclean.similarphoto.SimilarPhotoCluster
import com.nice.aceclean.util.MediaFileInfo
import com.nice.aceclean.util.MemorySnapshot
import com.nice.aceclean.util.RunningAppInfo
import com.nice.aceclean.util.DeviceReport
import com.nice.aceclean.util.BatterySnapshot
import com.nice.aceclean.notification.InterceptedNotification
import com.nice.aceclean.notification.NotificationStatsSnapshot
import com.nice.aceclean.util.InstalledAppInfo
import com.nice.aceclean.util.NetworkUsageSnapshot
import java.util.UUID

enum class ScanPermission { STORAGE, IMAGES, VIDEO, USAGE, NOTIFICATIONS, NONE }

/** Home feature routes share scanning, permission handoff, and completion advertising. */
enum class ScanFeature(val title: Int, val animation: Int, val destination: Class<*>, val permission: ScanPermission) {
    CLEAN_UP(R.string.clean_up, R.raw.clean_anim, CleanUpActivity::class.java, ScanPermission.STORAGE),
    NETWORK_TRAFFIC(R.string.network_traffic, R.raw.device_scan_scan_anim, NetworkTrafficActivity::class.java, ScanPermission.USAGE),
    DUPLICATE_PHOTO(R.string.duplicate_photo_cleaner, R.raw.picture_clean_scan_anim, SimilarPhotoCleanerActivity::class.java, ScanPermission.IMAGES),
    APP_MANAGER(R.string.app_manager, R.raw.app_ram_manager_scan_anim, AppManagerActivity::class.java, ScanPermission.USAGE),
    SCREENSHOT(R.string.screenshot_cleaner, R.raw.picture_clean_scan_anim, ScreenshotCleanerActivity::class.java, ScanPermission.IMAGES),
    BIG_FILE(R.string.big_file_cleaner, R.raw.big_file_scan_anim, BigFileCleanerActivity::class.java, ScanPermission.STORAGE),
    VIDEO(R.string.video_cleaner, R.raw.video_clean_scan_anim, VideoCleanerActivity::class.java, ScanPermission.VIDEO),
    NOTIFICATIONS(R.string.notification_cleaner, R.raw.notification_permission_guide, NotificationCleanerActivity::class.java, ScanPermission.NOTIFICATIONS),
    RAM(R.string.ram_status, R.raw.app_ram_manager_scan_anim, RamStatusActivity::class.java, ScanPermission.USAGE),
    DEVICE(R.string.device_scan, R.raw.device_scan_scan_anim, DeviceReportActivity::class.java, ScanPermission.NONE),
    BATTERY(R.string.battery_info, R.raw.battery_info_scan_anim, BatteryInfoActivity::class.java, ScanPermission.NONE);

    fun hasPermission(context: Context): Boolean = when (permission) {
        ScanPermission.STORAGE -> StorageAccess.hasBroadAccess(context)
        ScanPermission.USAGE -> SpecialPermissionAccess.isGranted(context, SpecialPermission.USAGE_ACCESS)
        ScanPermission.NOTIFICATIONS -> SpecialPermissionAccess.isGranted(context, SpecialPermission.NOTIFICATION_LISTENER)
        ScanPermission.NONE -> true
        ScanPermission.IMAGES, ScanPermission.VIDEO -> {
            val permissionName = if (Build.VERSION.SDK_INT >= 33) {
                if (permission == ScanPermission.VIDEO) android.Manifest.permission.READ_MEDIA_VIDEO
                else android.Manifest.permission.READ_MEDIA_IMAGES
            } else android.Manifest.permission.READ_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
        }
    }

    fun accepts(result: FeatureScanResult): Boolean = when (this) {
        CLEAN_UP -> result is FeatureScanResult.Cleanup
        NETWORK_TRAFFIC -> result is FeatureScanResult.Traffic
        DUPLICATE_PHOTO -> result is FeatureScanResult.Photos
        APP_MANAGER -> result is FeatureScanResult.Apps
        SCREENSHOT -> result is FeatureScanResult.Screenshots
        BIG_FILE, VIDEO -> result is FeatureScanResult.Files && result.feature == this
        NOTIFICATIONS -> result is FeatureScanResult.Notifications
        RAM -> result is FeatureScanResult.Ram
        DEVICE -> result is FeatureScanResult.Device
        BATTERY -> result is FeatureScanResult.Battery
    }

    fun scanIntent(context: Context) = Intent(context, FeatureScanActivity::class.java).putExtra(EXTRA_FEATURE, name)

    companion object {
        const val EXTRA_FEATURE = "scan_feature"
        const val EXTRA_RESULT = "scan_result_id"
        fun from(intent: Intent): ScanFeature? = entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_FEATURE) }
    }
}

sealed interface FeatureScanResult {
    data class Cleanup(val value: CleanupScanResult) : FeatureScanResult
    data class Traffic(val usage: NetworkUsageSnapshot, val apps: List<InstalledAppInfo>) : FeatureScanResult
    data class Photos(val clusters: List<SimilarPhotoCluster>) : FeatureScanResult
    data class Apps(val apps: List<InstalledAppInfo>) : FeatureScanResult
    data class Screenshots(val screenshots: List<MediaFileInfo>, val others: List<MediaFileInfo>) : FeatureScanResult
    data class Files(val feature: ScanFeature, val files: List<MediaFileInfo>) : FeatureScanResult
    data class Notifications(val stats: NotificationStatsSnapshot, val items: List<InterceptedNotification>) : FeatureScanResult
    data class Ram(val memory: MemorySnapshot, val apps: List<RunningAppInfo>) : FeatureScanResult
    data class Device(val report: DeviceReport) : FeatureScanResult
    data class Battery(val value: BatterySnapshot) : FeatureScanResult
}

/** Pass a small token through Intent rather than putting large file/photo lists in Binder. */
internal object FeatureScanResults {
    private val results = LinkedHashMap<String, FeatureScanResult>()
    fun put(result: FeatureScanResult): String {
        val id = UUID.randomUUID().toString()
        results[id] = result
        while (results.size > 4) results.remove(results.keys.first())
        return id
    }
    fun take(id: String?): FeatureScanResult? = results.remove(id)
}

/** Keeps a consumed handoff through Activity recreation; process death restarts a real scan. */
class FeatureResultViewModel : ViewModel() {
    private var result: FeatureScanResult? = null
    fun update(value: FeatureScanResult) { result = value }
    fun read(intent: Intent): FeatureScanResult? {
        if (result == null) result = FeatureScanResults.take(intent.getStringExtra(ScanFeature.EXTRA_RESULT))
        return result
    }
}

internal fun androidx.activity.ComponentActivity.scanResult(
    model: FeatureResultViewModel,
    feature: ScanFeature,
): FeatureScanResult? {
    val result = if (feature.hasPermission(this)) model.read(intent) else null
    if (result == null || !feature.accepts(result)) {
        startActivity(feature.scanIntent(this))
        finish()
        return null
    } else {
        com.nice.aceclean.util.LogUtils.d("scan_result_reused feature=$feature", "FeatureScan")
    }
    return result
}
