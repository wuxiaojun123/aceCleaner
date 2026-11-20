package com.nice.library_float

import android.content.Context


object PerformanceDashboardPreferenceStore {
    private const val PREFS_NAME = "performance_dashboard"
    private const val KEY_USER_CLOSED_DASHBOARD = "user_closed_dashboard"

    fun isClosedByUser(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USER_CLOSED_DASHBOARD, false)

    fun markClosedByUser(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USER_CLOSED_DASHBOARD, true)
            .apply()
    }

    fun markOpenedByUser(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USER_CLOSED_DASHBOARD, false)
            .apply()
    }
}
