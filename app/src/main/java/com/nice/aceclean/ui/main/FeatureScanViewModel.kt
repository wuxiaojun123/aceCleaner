package com.nice.aceclean.ui.main

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import com.nice.aceclean.util.DeviceToolsRepository
import com.nice.aceclean.util.MediaStoreRepository
import com.nice.aceclean.notification.InterceptedNotificationStore
import com.nice.aceclean.notification.NotificationStatsStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nice.aceclean.cleanup.CleanupRepository
import com.nice.aceclean.similarphoto.SimilarPhotoScanner
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.LogUtils
import com.nice.aceclean.util.NetworkUsageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeatureScanViewModel(application: Application) : AndroidViewModel(application) {
    data class State(val started: Boolean = false, val files: Int? = null,
        val result: FeatureScanResult? = null, val failed: Boolean = false)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    var permissionRequested = false
    var adAttempted = false
    var navigated = false

    fun scan(feature: ScanFeature) {
        if (mutableState.value.started) return
        mutableState.value = State(started = true)
        viewModelScope.launch {
            try {
                LogUtils.d("scan_start feature=$feature", TAG)
                val context = getApplication<Application>()
                val result = when (feature) {
                    ScanFeature.CLEAN_UP -> FeatureScanResult.Cleanup(CleanupRepository.scan(context) {
                        mutableState.value = mutableState.value.copy(files = it.scannedFiles)
                    })
                    ScanFeature.DUPLICATE_PHOTO -> FeatureScanResult.Photos(SimilarPhotoScanner.scan(context))
                    ScanFeature.APP_MANAGER -> withContext(Dispatchers.IO) {
                        FeatureScanResult.Apps(DeviceStats.launchableApps(context))
                    }
                    ScanFeature.NETWORK_TRAFFIC -> withContext(Dispatchers.IO) {
                        val usage = NetworkUsageRepository.currentMonth(context)
                        val apps = DeviceStats.launchableApps(context)
                            .map { it.copy(rxBytes = usage.bytesByUid[it.applicationInfo.uid] ?: 0L, txBytes = 0L) }
                            .filter { it.rxBytes > 0L }.sortedByDescending { it.rxBytes }.take(8)
                        FeatureScanResult.Traffic(usage, apps)
                    }
                    ScanFeature.SCREENSHOT -> withContext(Dispatchers.IO) {
                        val pictures = MediaStoreRepository.pictures(context)
                        FeatureScanResult.Screenshots(pictures.first, pictures.second)
                    }
                    ScanFeature.BIG_FILE, ScanFeature.VIDEO -> withContext(Dispatchers.IO) {
                        FeatureScanResult.Files(feature, if (feature == ScanFeature.BIG_FILE)
                            MediaStoreRepository.largeFiles(context) else MediaStoreRepository.videos(context))
                    }
                    ScanFeature.NOTIFICATIONS -> withContext(Dispatchers.IO) {
                        InterceptedNotificationStore.discardExpired(context)
                        FeatureScanResult.Notifications(NotificationStatsStore.load(context),
                            InterceptedNotificationStore.observeToday(context).value)
                    }
                    ScanFeature.RAM -> withContext(Dispatchers.IO) {
                        val apps = DeviceToolsRepository.runningApps(context)
                        FeatureScanResult.Ram(DeviceToolsRepository.memorySnapshot(context), apps)
                    }
                    ScanFeature.DEVICE -> withContext(Dispatchers.Default) {
                        FeatureScanResult.Device(DeviceToolsRepository.deviceReport(context))
                    }
                    ScanFeature.BATTERY -> withContext(Dispatchers.Default) {
                        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                            ?: error("Battery information unavailable")
                        FeatureScanResult.Battery(DeviceToolsRepository.batterySnapshot(context, battery))
                    }
                }
                LogUtils.d("scan_complete feature=$feature", TAG)
                mutableState.value = mutableState.value.copy(result = result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                LogUtils.e("scan_failed feature=$feature", TAG, error)
                mutableState.value = State(failed = true)
            }
        }
    }

    private companion object { const val TAG = "FeatureScan" }
}
