package com.nice.aceclean.ui.main

import android.content.Context
import java.util.Calendar

/** Records actual result visits, rather than home clicks or permission prompts. */
internal object FeatureUsageStore {
    private const val PREFS_NAME = "feature_daily_usage"

    fun markUsed(context: Context, feature: ScanFeature) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(feature.name, todayKey()).apply()
    }

    fun wasUsedToday(context: Context, feature: ScanFeature): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(feature.name, null) == todayKey()

    fun todayKey(): String = localDayKey(Calendar.getInstance())
}

internal fun localDayKey(calendar: Calendar): String =
    "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}-${calendar.get(Calendar.DAY_OF_MONTH)}"

internal enum class HomeFeatureBadgeState { PERMISSION_REQUIRED, UNUSED_TODAY, NONE }

internal fun homeFeatureBadgeState(
    hasPermission: Boolean,
    usedToday: Boolean,
    dailyReminder: Boolean,
): HomeFeatureBadgeState = when {
    !hasPermission -> HomeFeatureBadgeState.PERMISSION_REQUIRED
    dailyReminder && !usedToday -> HomeFeatureBadgeState.UNUSED_TODAY
    else -> HomeFeatureBadgeState.NONE
}
