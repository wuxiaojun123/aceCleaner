package com.nice.aceclean.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.lang.ref.WeakReference
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationCollectorService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceRef = WeakReference(this)
        connected = true
        clearEligibleNotifications()
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
        sbn?.takeIf(::isEligibleForCleaning)?.let { notification ->
            dismissedNotificationKeys += notification.key
            cancelNotification(notification.key)
        }
        publishNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publishNotifications()
    }

    private fun publishNotifications() {
        _notifications.value = runCatching {
            activeNotifications.filter(::shouldDisplay)
        }.getOrDefault(emptyList())
    }

    /** Mirrors the competitor: clean only other apps' clearable, non-ongoing notifications. */
    private fun clearEligibleNotifications() {
        runCatching {
            activeNotifications.filter(::isEligibleForCleaning).forEach { notification ->
                dismissedNotificationKeys += notification.key
                cancelNotification(notification.key)
            }
        }
        publishNotifications()
    }

    private fun isEligibleForCleaning(notification: StatusBarNotification): Boolean =
        notification.packageName != packageName &&
            !notification.isOngoing &&
            notification.isClearable

    private fun shouldDisplay(notification: StatusBarNotification): Boolean =
        isEligibleForCleaning(notification) && notification.key !in dismissedNotificationKeys

    companion object {
        private var serviceRef = WeakReference<NotificationCollectorService>(null)
        private val _notifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
        private val dismissedNotificationKeys = Collections.synchronizedSet(mutableSetOf<String>())
        val notificationFlow: StateFlow<List<StatusBarNotification>> = _notifications.asStateFlow()
        @Volatile var connected: Boolean = false
            private set

        fun notifications(): List<StatusBarNotification> = notificationFlow.value

        fun clearAll(): Boolean {
            val service = serviceRef.get() ?: return false
            _notifications.value.forEach { notification ->
                dismissedNotificationKeys += notification.key
                service.cancelNotification(notification.key)
            }
            _notifications.value = emptyList()
            return true
        }

        fun clear(key: String): Boolean {
            val service = serviceRef.get() ?: return false
            dismissedNotificationKeys += key
            service.cancelNotification(key)
            _notifications.value = _notifications.value.filterNot { it.key == key }
            return true
        }
    }
}
