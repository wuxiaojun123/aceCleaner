package com.nice.aceclean.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.lang.ref.WeakReference

class NotificationCollectorService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceRef = WeakReference(this)
    }

    override fun onListenerDisconnected() {
        serviceRef.clear()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        serviceRef.clear()
        super.onDestroy()
    }

    companion object {
        private var serviceRef = WeakReference<NotificationCollectorService>(null)

        fun notifications(): List<StatusBarNotification> = runCatching {
            serviceRef.get()?.activeNotifications
                ?.filterNot { it.packageName == serviceRef.get()?.packageName }
                .orEmpty()
        }.getOrDefault(emptyList())

        fun clearAll(): Boolean {
            val service = serviceRef.get() ?: return false
            service.cancelAllNotifications()
            return true
        }

        fun clear(key: String): Boolean {
            val service = serviceRef.get() ?: return false
            service.cancelNotification(key)
            return true
        }
    }
}
