package com.nice.aceclean.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nice.aceclean.R
import com.nice.aceclean.ui.main.BatteryInfoActivity
import com.nice.aceclean.ui.main.CleanUpActivity
import com.nice.aceclean.ui.main.MainActivity
import com.nice.aceclean.ui.main.NetworkTrafficActivity
import com.nice.aceclean.ui.main.RamStatusActivity
import com.nice.aceclean.ui.main.ScreenshotCleanerActivity
import com.nice.aceclean.util.LocaleHelper
import com.nice.aceclean.util.NetworkSpeedSampler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StickyNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var speedUpdateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0L))
        startSpeedUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (speedUpdateJob?.isActive != true) startSpeedUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        speedUpdateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSpeedUpdates() {
        speedUpdateJob?.cancel()
        speedUpdateJob = serviceScope.launch {
            val speedSampler = NetworkSpeedSampler()
            while (isActive) {
                delay(SPEED_UPDATE_INTERVAL_MS)
                val bytesPerSecond = speedSampler.sampleBytesPerSecond()
                val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this@StickyNotificationService,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                if (!notificationsAllowed) continue
                try {
                    NotificationManagerCompat.from(this@StickyNotificationService).notify(
                        NOTIFICATION_ID,
                        buildNotification(bytesPerSecond),
                    )
                } catch (_: SecurityException) {
                    // Permission can be revoked between the check and the notification update.
                }
            }
        }
    }

    private fun buildNotification(bytesPerSecond: Long): Notification {
        val localizedContext = LocaleHelper.wrapContext(this)
        val smallViews = buildRemoteViews(
            R.layout.layout_notification_sticky,
            localizedContext,
            bytesPerSecond,
        )
        val bigViews = buildRemoteViews(
            R.layout.layout_notification_sticky_big,
            localizedContext,
            bytesPerSecond,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sticky_notification)
            .setContentTitle(localizedContext.getString(R.string.app_name))
            .setColor(ContextCompat.getColor(this, R.color.notification_sticky_background))
            .setColorized(true)
            .setCustomContentView(smallViews)
            .setContentIntent(activityPendingIntent(MainActivity::class.java, REQUEST_HOME))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(bigViews)
        return builder.build()
    }

    private fun buildRemoteViews(
        layoutId: Int,
        localizedContext: Context,
        bytesPerSecond: Long,
    ) =
        RemoteViews(packageName, layoutId).apply {
            val speed = NetworkSpeedSampler.format(bytesPerSecond)
                .replace(" ", "\n") + localizedContext.getString(R.string.notification_speed_suffix)
            setTextViewText(
                R.id.notification_network_speed,
                speed,
            )

            setOnClickPendingIntent(
                R.id.notification_screenshot,
                activityPendingIntent(ScreenshotCleanerActivity::class.java, REQUEST_SCREENSHOT),
            )
            setOnClickPendingIntent(
                R.id.notification_clean_up,
                activityPendingIntent(CleanUpActivity::class.java, REQUEST_CLEAN_UP),
            )
            setOnClickPendingIntent(
                R.id.notification_ram,
                activityPendingIntent(RamStatusActivity::class.java, REQUEST_RAM),
            )
            setOnClickPendingIntent(
                R.id.notification_battery,
                activityPendingIntent(BatteryInfoActivity::class.java, REQUEST_BATTERY),
            )
            setOnClickPendingIntent(
                R.id.notification_network_speed,
                activityPendingIntent(NetworkTrafficActivity::class.java, REQUEST_NETWORK),
            )
            if (layoutId == R.layout.layout_notification_sticky_big) {
                setOnClickPendingIntent(
                    R.id.notification_clean_cache,
                    activityPendingIntent(CleanUpActivity::class.java, REQUEST_CLEAN_CACHE),
                )
            }
        }

    private fun activityPendingIntent(target: Class<*>, requestCode: Int): PendingIntent {
        val intent = Intent(this, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "sticky_cleaner"
        private const val NOTIFICATION_ID = 1_001
        private const val SPEED_UPDATE_INTERVAL_MS = 5_000L
        private const val REQUEST_HOME = 10
        private const val REQUEST_SCREENSHOT = 11
        private const val REQUEST_CLEAN_UP = 12
        private const val REQUEST_RAM = 13
        private const val REQUEST_BATTERY = 14
        private const val REQUEST_NETWORK = 15
        private const val REQUEST_CLEAN_CACHE = 16

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StickyNotificationService::class.java),
            )
        }
    }
}
