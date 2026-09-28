package com.nice.aceclean.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nice.aceclean.notification.AppIconLoader
import com.nice.aceclean.notification.InterceptedNotificationStore
import com.nice.aceclean.notification.NotificationInterceptStore
import com.nice.aceclean.notification.NotificationStatsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationCollectorService : NotificationListenerService() {
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return

        val appName = AppIconLoader.loadLabel(this, sbn.packageName) ?: sbn.packageName
        NotificationStatsStore.recordNotification(this, sbn.packageName, appName)
        if (!isInterceptable(sbn) || !NotificationInterceptStore.shouldIntercept(this, sbn.packageName)) {
            return
        }

        val interceptedAt = System.currentTimeMillis()
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            .ifBlank { appName }
        val content = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )?.toString().orEmpty()

        cancelNotification(sbn.key)
        NotificationInterceptStore.recordIntercepted(this, interceptedAt)
        historyScope.launch {
            InterceptedNotificationStore.record(
                context = this@NotificationCollectorService,
                notificationKey = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                content = content,
                postedAt = sbn.postTime,
                interceptedAt = interceptedAt,
            )
        }
    }

    private fun isInterceptable(sbn: StatusBarNotification): Boolean {
        val flags = sbn.notification.flags
        return sbn.isClearable &&
            !sbn.isOngoing &&
            flags and Notification.FLAG_ONGOING_EVENT == 0 &&
            flags and Notification.FLAG_FOREGROUND_SERVICE == 0 &&
            flags and Notification.FLAG_NO_CLEAR == 0
    }

    override fun onDestroy() {
        historyScope.cancel()
        super.onDestroy()
    }
}
