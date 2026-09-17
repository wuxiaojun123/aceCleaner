package com.nice.aceclean.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkSpeedSamplerTest {

    @Test
    fun `sample uses combined-byte delta and actual elapsed time`() {
        val bytes = ArrayDeque(listOf(1_000L, 5_000L))
        val times = ArrayDeque(listOf(2_000L, 4_000L))
        val sampler = NetworkSpeedSampler(
            byteCounter = { bytes.removeFirst() },
            clock = { times.removeFirst() },
        )

        assertEquals(2_000L, sampler.sampleBytesPerSecond())
    }

    @Test
    fun `formatter matches compact binary traffic units`() {
        assertEquals("0 B", NetworkSpeedSampler.format(0L))
        assertEquals("1.5 KB", NetworkSpeedSampler.format(1_536L))
        assertEquals("2.0 MB", NetworkSpeedSampler.format(2L * 1_024L * 1_024L))
    }
}
