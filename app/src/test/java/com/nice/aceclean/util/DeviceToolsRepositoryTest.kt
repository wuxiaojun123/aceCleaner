package com.nice.aceclean.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceToolsRepositoryTest {

    @Test
    fun calculatePercent_handlesNormalAndInvalidValues() {
        assertEquals(75, calculatePercent(750L, 1_000L))
        assertEquals(0, calculatePercent(10L, 0L))
        assertEquals(0, calculatePercent(-10L, 100L))
        assertEquals(100, calculatePercent(150L, 100L))
    }

    @Test
    fun megabitsPerSecond_usesDecimalNetworkUnits() {
        assertEquals(8.0, NetworkSpeedTest.megabitsPerSecond(1_000_000L, 1_000_000_000L), 0.001)
        assertEquals(0.0, NetworkSpeedTest.megabitsPerSecond(0L, 1_000_000_000L), 0.001)
        assertEquals(0.0, NetworkSpeedTest.megabitsPerSecond(1_000L, 0L), 0.001)
    }
}
