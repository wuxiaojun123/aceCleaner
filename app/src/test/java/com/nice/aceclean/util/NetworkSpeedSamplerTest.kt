package com.nice.aceclean.util

import com.nice.aceclean.R
import java.util.Locale
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
        assertEquals("0 B", format(0L, Locale.US))
        assertEquals("0 B", format(-1L, Locale.US))
        assertEquals("1.5 KB", format(1_536L, Locale.US))
        assertEquals("2.0 MB", format(2L * 1_024L * 1_024L, Locale.US))
        assertEquals("1.0 GB", format(1_024L * 1_024L * 1_024L, Locale.US))
    }

    @Test
    fun `formatter uses localized decimal separator`() {
        assertEquals("1,5 KB", format(1_536L, Locale.GERMAN))
    }

    private fun format(bytes: Long, locale: Locale): String = NetworkSpeedSampler.format(bytes) { resource, value ->
        val pattern = when (resource) {
            R.string.string_size_bytes -> "%1\$d B"
            R.string.string_size_kilobytes -> "%1\$.1f KB"
            R.string.string_size_megabytes -> "%1\$.1f MB"
            R.string.string_size_gigabytes -> "%1\$.1f GB"
            else -> error("Unexpected size resource: $resource")
        }
        String.format(locale, pattern, value)
    }
}
