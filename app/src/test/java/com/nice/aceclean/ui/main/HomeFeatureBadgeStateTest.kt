package com.nice.aceclean.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HomeFeatureBadgeStateTest {
    @Test fun missingPermissionAlwaysWins() {
        for (used in listOf(false, true)) {
            for (reminder in listOf(false, true)) {
                assertEquals(HomeFeatureBadgeState.PERMISSION_REQUIRED, homeFeatureBadgeState(false, used, reminder))
            }
        }
    }

    @Test fun dailyDotDisappearsAfterUse() {
        assertEquals(HomeFeatureBadgeState.UNUSED_TODAY, homeFeatureBadgeState(true, false, true))
        assertEquals(HomeFeatureBadgeState.NONE, homeFeatureBadgeState(true, true, true))
    }

    @Test fun secondaryToolsDoNotShowDailyDots() {
        assertEquals(HomeFeatureBadgeState.NONE, homeFeatureBadgeState(true, false, false))
    }

    @Test fun dayChangesAtLocalMidnightIncludingNewYear() {
        val local = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00")).apply {
            clear()
            set(2026, Calendar.DECEMBER, 31, 23, 59, 59)
        }
        val yesterday = localDayKey(local)
        local.add(Calendar.SECOND, 1)
        assertNotEquals(yesterday, localDayKey(local))
        val today = localDayKey(local)
        local.add(Calendar.HOUR_OF_DAY, 12)
        assertEquals(today, localDayKey(local))
    }
}
