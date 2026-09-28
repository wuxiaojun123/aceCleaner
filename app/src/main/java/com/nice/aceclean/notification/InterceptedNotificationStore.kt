package com.nice.aceclean.notification

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InterceptedNotification(
    val id: Long,
    val notificationKey: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val postedAt: Long,
    val interceptedAt: Long,
    val dayKey: String,
)

object InterceptedNotificationStore {
    private const val PREFS_NAME = "intercepted_notifications"
    private const val KEY_ITEMS = "items"
    private val itemsFlow = MutableStateFlow<List<InterceptedNotification>>(emptyList())
    private var loadedDay: String? = null

    @Synchronized
    fun observeToday(context: Context): StateFlow<List<InterceptedNotification>> {
        ensureLoaded(context)
        return itemsFlow.asStateFlow()
    }

    @Synchronized
    fun record(
        context: Context,
        notificationKey: String,
        packageName: String,
        appName: String,
        title: String,
        content: String,
        postedAt: Long,
        interceptedAt: Long = System.currentTimeMillis(),
    ) {
        if (interceptedAt <= NotificationInterceptStore.lastClearedAt(context)) return
        ensureLoaded(context)
        val item = InterceptedNotification(
            id = interceptedAt xor notificationKey.hashCode().toLong(),
            notificationKey = notificationKey,
            packageName = packageName,
            appName = appName,
            title = title,
            content = content,
            postedAt = postedAt,
            interceptedAt = interceptedAt,
            dayKey = todayKey(),
        )
        val updated = listOf(item) + itemsFlow.value.filterNot { it.notificationKey == notificationKey }
        persist(context, updated)
    }

    @Synchronized
    fun clearTodayThrough(context: Context, cutoff: Long) {
        ensureLoaded(context)
        persist(context, itemsFlow.value.filter { it.interceptedAt > cutoff })
    }

    @Synchronized
    fun discardExpired(context: Context) {
        loadedDay = null
        ensureLoaded(context)
    }

    private fun ensureLoaded(context: Context) {
        val today = todayKey()
        if (loadedDay == today) return
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]").orEmpty()
        val loaded = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    if (item.optString("dayKey") == today) add(item.toModel())
                }
            }
        }.getOrDefault(emptyList()).sortedByDescending(InterceptedNotification::interceptedAt)
        loadedDay = today
        persist(context, loaded)
    }

    private fun persist(context: Context, items: List<InterceptedNotification>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, array.toString()).apply()
        itemsFlow.value = items
    }

    private fun InterceptedNotification.toJson() = JSONObject().apply {
        put("id", id)
        put("notificationKey", notificationKey)
        put("packageName", packageName)
        put("appName", appName)
        put("title", title)
        put("content", content)
        put("postedAt", postedAt)
        put("interceptedAt", interceptedAt)
        put("dayKey", dayKey)
    }

    private fun JSONObject.toModel() = InterceptedNotification(
        id = optLong("id"),
        notificationKey = optString("notificationKey"),
        packageName = optString("packageName"),
        appName = optString("appName"),
        title = optString("title"),
        content = optString("content"),
        postedAt = optLong("postedAt"),
        interceptedAt = optLong("interceptedAt"),
        dayKey = optString("dayKey"),
    )

    private fun todayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}
