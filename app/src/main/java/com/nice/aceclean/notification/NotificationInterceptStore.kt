package com.nice.aceclean.notification

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationInterceptStore {
    const val DEFAULT_ENABLED = true

    private const val PREFS_NAME = "notification_intercept"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_DAY = "day"
    private const val KEY_INTERCEPTED_TOTAL = "intercepted_total"
    private const val KEY_LAST_CLEARED_AT = "last_cleared_at"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun whitelist(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_WHITELIST, emptySet()).orEmpty()

    fun isWhitelisted(context: Context, packageName: String): Boolean =
        packageName in whitelist(context)

    fun setWhitelisted(context: Context, packageName: String, allowed: Boolean) {
        val current = whitelist(context).toMutableSet()
        if (allowed) current.add(packageName) else current.remove(packageName)
        prefs(context).edit().putStringSet(KEY_WHITELIST, current).apply()
    }

    fun shouldIntercept(context: Context, packageName: String): Boolean =
        isEnabled(context) && !isWhitelisted(context, packageName)

    @Synchronized
    fun recordIntercepted(context: Context, interceptedAt: Long = System.currentTimeMillis()) {
        resetIfNeeded(context)
        val preferences = prefs(context)
        if (interceptedAt <= preferences.getLong(KEY_LAST_CLEARED_AT, 0L)) return
        preferences.edit()
            .putString(KEY_DAY, todayKey())
            .putInt(KEY_INTERCEPTED_TOTAL, preferences.getInt(KEY_INTERCEPTED_TOTAL, 0) + 1)
            .apply()
    }

    fun interceptedToday(context: Context): Int {
        resetIfNeeded(context)
        return prefs(context).getInt(KEY_INTERCEPTED_TOTAL, 0)
    }

    fun clearInterceptedToday(context: Context, clearedAt: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putString(KEY_DAY, todayKey())
            .putInt(KEY_INTERCEPTED_TOTAL, 0)
            .putLong(KEY_LAST_CLEARED_AT, clearedAt)
            .apply()
    }

    fun lastClearedAt(context: Context): Long {
        resetIfNeeded(context)
        return prefs(context).getLong(KEY_LAST_CLEARED_AT, 0L)
    }

    private fun resetIfNeeded(context: Context) {
        val preferences = prefs(context)
        val today = todayKey()
        if (preferences.getString(KEY_DAY, null) == today) return
        preferences.edit()
            .putString(KEY_DAY, today)
            .putInt(KEY_INTERCEPTED_TOTAL, 0)
            .putLong(KEY_LAST_CLEARED_AT, 0L)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun todayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}
