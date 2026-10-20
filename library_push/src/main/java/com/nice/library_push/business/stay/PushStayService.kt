package com.nice.library_push.business.stay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nice.library_push.R

class PushStayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.nice_push_stay_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val launcher = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launcher?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.nice_push_icon)
            .setContentTitle(intent?.getStringExtra(EXTRA_TITLE).orEmpty())
            .setContentText(intent?.getStringExtra(EXTRA_TEXT).orEmpty())
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIFICATION_ID, notification)
        } catch (_: RuntimeException) { stopSelf(startId) }
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_TITLE = "nice_push_stay_title"
        const val EXTRA_TEXT = "nice_push_stay_text"
        private const val CHANNEL_ID = "nice_push_stay"
        private const val NOTIFICATION_ID = 3100
    }
}
