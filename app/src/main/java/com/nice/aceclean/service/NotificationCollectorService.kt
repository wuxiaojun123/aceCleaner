package com.nice.aceclean.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationCollectorService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceRef = WeakReference(this)
        connected = true
        publishNotifications()
    }

    override fun onListenerDisconnected() {
        serviceRef.clear()
        connected = false
        _notifications.value = emptyList()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        serviceRef.clear()
        connected = false
        _notifications.value = emptyList()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publishNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publishNotifications()
    }

    private fun publishNotifications() {
        _notifications.value = runCatching {
            activeNotifications.filterNot { it.packageName == packageName }
        }.getOrDefault(emptyList())
    }

    companion object {
        private var serviceRef = WeakReference<NotificationCollectorService>(null)
        private val _notifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
        val notificationFlow: StateFlow<List<StatusBarNotification>> = _notifications.asStateFlow()
        @Volatile var connected: Boolean = false
            private set

        fun notifications(): List<StatusBarNotification> = notificationFlow.value

        fun clearAll(): Boolean {
            val service = serviceRef.get() ?: return false
            service.cancelAllNotifications()
            _notifications.value = emptyList()
            return true
        }

        fun clear(key: String): Boolean {
            val service = serviceRef.get() ?: return false
            service.cancelNotification(key)
            _notifications.value = _notifications.value.filterNot { it.key == key }
            return true
        }
    }
}
