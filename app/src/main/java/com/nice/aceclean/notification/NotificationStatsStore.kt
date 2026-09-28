package com.nice.aceclean.notification

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotificationAppStats(
    val packageName: String,
    val appName: String,
    val count: Int,
)

data class NotificationStatsSnapshot(
    val totalToday: Int,
    val topApps: List<NotificationAppStats>,
)

object NotificationStatsStore {
    private const val PREFS_NAME = "notification_stats"
    private const val KEY_DAY = "day"
    private const val KEY_PACKAGES = "packages"
    private const val KEY_TOTAL = "total"
    private const val KEY_COUNT_PREFIX = "count_"
    private const val KEY_NAME_PREFIX = "name_"

    @Synchronized
    fun recordNotification(context: Context, packageName: String, appName: String) {
        resetIfNeeded(context)
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val packages = preferences.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toMutableSet()
        packages += packageName
        preferences.edit()
            .putString(KEY_DAY, todayKey())
            .putStringSet(KEY_PACKAGES, packages)
            .putString(KEY_NAME_PREFIX + packageName, appName)
            .putInt(KEY_COUNT_PREFIX + packageName, preferences.getInt(KEY_COUNT_PREFIX + packageName, 0) + 1)
            .putInt(KEY_TOTAL, preferences.getInt(KEY_TOTAL, 0) + 1)
            .apply()
    }

    fun load(context: Context): NotificationStatsSnapshot {
        resetIfNeeded(context)
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apps = preferences.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().map { packageName ->
            NotificationAppStats(
                packageName = packageName,
                appName = AppIconLoader.loadLabel(context, packageName)
                    ?: preferences.getString(KEY_NAME_PREFIX + packageName, packageName).orEmpty(),
                count = preferences.getInt(KEY_COUNT_PREFIX + packageName, 0),
            )
        }.filter { it.count > 0 }.sortedByDescending(NotificationAppStats::count)
        return NotificationStatsSnapshot(preferences.getInt(KEY_TOTAL, 0), apps.take(5))
    }

    private fun resetIfNeeded(context: Context) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = todayKey()
        if (preferences.getString(KEY_DAY, null) != today) {
            preferences.edit().clear().putString(KEY_DAY, today).apply()
        }
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}
