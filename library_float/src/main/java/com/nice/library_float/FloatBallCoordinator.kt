package com.nice.library_float

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import androidx.core.content.ContextCompat
import com.nice.library_float.R
import java.io.File


object FloatBallCoordinator {
    private const val TAG = "FloatBallCoordinator"
    private val FIRST_CHECK_DELAY_MS = if (FloatingBall.debugTiming) {
        45_000L
    } else {
        65_000L
    }
    private val COOLDOWN_DURATION_MS = if (FloatingBall.debugTiming) {
        1 * 60_000L
    } else {
        3 * 60_000L
    }
    private const val TIP_AUTO_DISMISS_DELAY_MS = 6_000L
    private const val DOWNLOAD_FILE_EVENTS = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
    private const val FILE_OBSERVER_IS_DIRECTORY = 0x40000000

    private val handler = Handler(Looper.getMainLooper())
    private var isAppInBackground = false
    private var backgroundStartedAtMs = 0L
    private var lastTipShownAtMs = 0L
    private var interceptedNotificationCountAtBackground = 0
    private var hasNewImageSinceBackground = false
    private var hasNewDownloadSinceBackground = false
    private var latestDownloadSizeBytes = 0L
    private var imageObserverRegistered = false
    private var downloadFileObserver: FileObserver? = null

    private val imageObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            onImageLibraryChanged()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            onImageLibraryChanged()
        }
    }

    private val evaluateRunnable = Runnable {
        LogUtils.d("deal--evaluateRunnable--检查",TAG)
        evaluateBackgroundState()
    }
    private val dismissTipRunnable = Runnable {
        FloatManager.dismissTipFloat()
        if (isAppInBackground) {
            FloatManager.restoreDefaultSliderFloat(FloatingBall.context.applicationContext)
        }
        LogUtils.d("tip float dismissed after ${TIP_AUTO_DISMISS_DELAY_MS}ms", TAG)
    }

    
    fun onAppBackground(context: Context) {
        stopBackgroundSession()
        val appContext = context.applicationContext
        if (PerformanceDashboardPreferenceStore.isClosedByUser(appContext)) {
            LogUtils.d("skip background session: disabled by user", TAG)
            onUserDisabled()
            return
        }
        if (!hasOverlayPermission(appContext)) {
            FloatManager.dismissAllFloatWindows()
            LogUtils.d("skip background session: overlay permission is missing", TAG)
            return
        }

        isAppInBackground = true
        backgroundStartedAtMs = SystemClock.elapsedRealtime()
        lastTipShownAtMs = 0L
        interceptedNotificationCountAtBackground =
            FloatingBall.host.interceptedToday(appContext)
        hasNewImageSinceBackground = false
        hasNewDownloadSinceBackground = false
        latestDownloadSizeBytes = 0L
        handler.removeCallbacks(evaluateRunnable)
        handler.removeCallbacks(dismissTipRunnable)
        registerImageObserver(appContext)
        registerDownloadFileObserver(appContext)

        showDefaultState(appContext)
        handler.postDelayed(evaluateRunnable, FIRST_CHECK_DELAY_MS)
        LogUtils.d(
            "background session started, interceptedNotificationBase=" +
                interceptedNotificationCountAtBackground,
            TAG,
        )
    }

    
    fun onAppForeground() {
        stopBackgroundSession()
        FloatManager.dismissPanels()
        FloatManager.hideSliderFloat()
        FloatManager.dismissTipFloat()
        LogUtils.d("background session cancelled", TAG)
    }

    
    fun onUserDisabled() {
        stopBackgroundSession()
        FloatManager.dismissAllFloatWindows()
        LogUtils.d("floating ball disabled by user", TAG)
    }

    private fun stopBackgroundSession() {
        isAppInBackground = false
        backgroundStartedAtMs = 0L
        lastTipShownAtMs = 0L
        interceptedNotificationCountAtBackground = 0
        hasNewImageSinceBackground = false
        hasNewDownloadSinceBackground = false
        latestDownloadSizeBytes = 0L
        handler.removeCallbacks(evaluateRunnable)
        handler.removeCallbacks(dismissTipRunnable)
        unregisterImageObserver()
        unregisterDownloadFileObserver()
    }

    
    private fun evaluateBackgroundState() {
        if (!isAppInBackground) return

        val context = FloatingBall.context.applicationContext
        if (!hasOverlayPermission(context)) {
            LogUtils.d("stop background session: overlay permission was revoked", TAG)
            onAppForeground()
            FloatManager.dismissAllFloatWindows()
            return
        }

        val nowMs = SystemClock.elapsedRealtime()
        val remainingCooldownMs =
            (lastTipShownAtMs + COOLDOWN_DURATION_MS - nowMs).coerceAtLeast(0L)
        if (lastTipShownAtMs > 0L && remainingCooldownMs > 0L) {
            handler.postDelayed(evaluateRunnable, remainingCooldownMs)
            LogUtils.d("tip cooldown active, remaining=${remainingCooldownMs}ms", TAG)
            return
        }

        val elapsedMs = (nowMs - backgroundStartedAtMs).coerceAtLeast(0L)
        val state = when {
            requiresPermissionTip(context) -> FloatBallState.FILES_ACCESS_NEEDED
            elapsedMs < COOLDOWN_DURATION_MS -> FloatBallState.DEFAULT
            else -> resolveState(context, elapsedMs)
        }
        applyState(context, state)

        if (state == FloatBallState.DEFAULT) {

            handler.postDelayed(
                evaluateRunnable,
                (COOLDOWN_DURATION_MS - elapsedMs).coerceAtLeast(0L),
            )
        } else {
            when (state) {
                FloatBallState.IMAGE_CLEAN -> {

                    hasNewImageSinceBackground = false
                }
                FloatBallState.JUNK_CLEAN -> {

                    hasNewDownloadSinceBackground = false
                    latestDownloadSizeBytes = 0L
                }
                else -> Unit
            }
            lastTipShownAtMs = nowMs
            scheduleTipAutoDismiss()
            handler.postDelayed(evaluateRunnable, COOLDOWN_DURATION_MS)
        }
    }

    
    private fun resolveState(context: Context, elapsedMs: Long): FloatBallState = FloatReminderPolicy.resolve(
        missingPermission = requiresPermissionTip(context),
        cooldownElapsed = elapsedMs >= COOLDOWN_DURATION_MS,
        newDownload = hasNewDownloadSinceBackground,
        newImage = hasNewImageSinceBackground,
        newNotifications = newInterceptedNotificationCount(context),
        memoryUsedPercent = MemoryUsageUtils.getMemoryUsedPercent(context),
    )

    
    private fun applyState(context: Context, state: FloatBallState) {
        when (state) {
            FloatBallState.DEFAULT -> {
                showDefaultState(context)
            }
            FloatBallState.FILES_ACCESS_NEEDED -> {
                FloatManager.showFloatTips(
                    context,
                    R.mipmap.ic_float_warning,
                    showPermissionContent(),
                    FloatTipType.PERMISSION,
                    memoryPercentText(context)
                )
            }
            FloatBallState.JUNK_CLEAN -> {
                FloatManager.showFloatTips(
                    context,
                    R.mipmap.ic_float_notice,
                    downloadJunkTipText(context),
                    FloatTipType.JUNK,
                )
            }
            FloatBallState.IMAGE_CLEAN -> {
                FloatManager.showFloatTips(
                    context,
                    R.mipmap.ic_float_notice,
                    context.getString(R.string.string_float_new_photos_found),
                    FloatTipType.IMAGE,
                )
            }
            FloatBallState.NOTIFICATION_CLEAN -> {
                FloatManager.showFloatTips(
                    context,
                    R.mipmap.ic_float_notice,
                    context.getString(R.string.string_float_too_many_notifications),
                    FloatTipType.NOTIFICATION,
                )
            }
            FloatBallState.PROCESS_CLEAN -> {
                FloatManager.showFloatTips(
                    context,
                    R.mipmap.ic_float_notice,
                    context.getString(R.string.string_float_too_many_apps_running),
                    FloatTipType.PROCESS,
                )
            }
            FloatBallState.NETWORK_TEST -> {
                FloatManager.showFloatTips(
                    context,
                    R.mipmap.ic_float_notice,
                    context.getString(R.string.string_float_slow_internet_test_speed),
                    FloatTipType.NETWORK_SPEED,
                )
            }
        }
        LogUtils.d("apply state=$state", TAG)
    }

    
    private fun scheduleTipAutoDismiss() {
        handler.removeCallbacks(dismissTipRunnable)
        handler.postDelayed(dismissTipRunnable, TIP_AUTO_DISMISS_DELAY_MS)
    }

    
    private fun showDefaultState(context: Context) {
        FloatManager.restoreDefaultSliderFloat(context)
    }

    
    private fun newInterceptedNotificationCount(context: Context): Int =
        (
            FloatingBall.host.interceptedToday(context) -
                interceptedNotificationCountAtBackground
            ).coerceAtLeast(0)

    
    private fun downloadJunkTipText(context: Context): String =
        context.getString(
            R.string.string_float_download_junk_found,
            Formatter.formatShortFileSize(context, latestDownloadSizeBytes),
        )

    
    private fun registerImageObserver(context: Context) {
        if (imageObserverRegistered || !hasImageReadAccess(context)) return
        imageObserverRegistered = runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                imageObserver,
            )
            true
        }.getOrElse {
            LogUtils.e("register image observer failed: ${it.message}", TAG)
            false
        }
    }

    private fun unregisterImageObserver() {
        if (!imageObserverRegistered) return
        runCatching {
            FloatingBall.context.contentResolver.unregisterContentObserver(imageObserver)
        }
            .onFailure { LogUtils.e("unregister image observer failed: ${it.message}", TAG) }
        imageObserverRegistered = false
    }

    private fun onImageLibraryChanged() {
        if (!isAppInBackground) return
        hasNewImageSinceBackground = true
        LogUtils.d("new image detected; queued for the next three-minute evaluation", TAG)
    }

    
    private fun registerDownloadFileObserver(context: Context) {
        if (downloadFileObserver != null || !hasStorageAccess(context)) return
        val downloadDirectory = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        )
        if (!downloadDirectory.isDirectory) {
            LogUtils.d("skip download observer: directory is unavailable", TAG)
            return
        }

        val observer = object : FileObserver(downloadDirectory.absolutePath, DOWNLOAD_FILE_EVENTS) {
            override fun onEvent(event: Int, path: String?) {
                if (!isAppInBackground || path.isNullOrEmpty() ||
                    event and FILE_OBSERVER_IS_DIRECTORY != 0 ||
                    event and DOWNLOAD_FILE_EVENTS == 0
                ) {
                    return
                }
                handler.post { onDownloadFileCompleted(File(downloadDirectory, path)) }
            }
        }
        runCatching {
            observer.startWatching()
            downloadFileObserver = observer
        }.onFailure {
            LogUtils.e("register download observer failed: ${it.message}", TAG)
        }
    }

    private fun unregisterDownloadFileObserver() {
        downloadFileObserver?.stopWatching()
        downloadFileObserver = null
    }

    private fun onDownloadFileCompleted(file: File) {
        if (!isAppInBackground || !file.isFile) return
        hasNewDownloadSinceBackground = true
        latestDownloadSizeBytes = file.length().coerceAtLeast(0L)
        LogUtils.d("new download detected: ${file.name}, size=$latestDownloadSizeBytes", TAG)
    }

    
    internal fun hasNotificationListenerAccess(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabledListeners.split(':').any { listener ->
            listener.contains(context.packageName, ignoreCase = true)
        }
    }

    private fun requiresPermissionTip(context: Context): Boolean =
        !hasStorageAccess(context) || !hasNotificationListenerAccess(context)

    
    internal fun hasStorageAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> true
        else -> ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasImageReadAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            Environment.isExternalStorageManager() ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES,
                ) == PackageManager.PERMISSION_GRANTED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> true
        else -> ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    
    private fun hasOverlayPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun memoryPercentText(context: Context): String =
        context.getString(R.string.string_float_percent, MemoryUsageUtils.getMemoryUsedPercent(context))

    
    private fun showPermissionContent(): String {
        val context = FloatingBall.context.applicationContext
        return when {
            !hasStorageAccess(context) -> context.getString(R.string.string_float_permission_files_access_needed)
            !hasNotificationListenerAccess(context) ->
                context.getString(R.string.string_float_permission_notification_access_needed)
            else -> ""
        }
    }

}
