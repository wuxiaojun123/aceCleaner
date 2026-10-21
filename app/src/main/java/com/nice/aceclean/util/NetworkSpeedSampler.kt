package com.nice.aceclean.util

import android.content.Context
import android.net.TrafficStats
import android.os.SystemClock
import com.nice.aceclean.R

/** Samples the device's combined download and upload traffic. */
class NetworkSpeedSampler(
    private val byteCounter: () -> Long = ::totalTrafficBytes,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {

    private var previousBytes = byteCounter()
    private var previousTime = clock()

    fun sampleBytesPerSecond(): Long {
        val currentBytes = byteCounter()
        val currentTime = clock()
        val elapsedMillis = (currentTime - previousTime).coerceAtLeast(1L)
        val transferredBytes = (currentBytes - previousBytes).coerceAtLeast(0L)

        previousBytes = currentBytes
        previousTime = currentTime
        return transferredBytes * 1_000L / elapsedMillis
    }

    companion object {
        private const val KIB = 1_024.0
        private const val MIB = KIB * KIB
        private const val GIB = MIB * KIB

        fun totalTrafficBytes(): Long =
            TrafficStats.getTotalRxBytes().validTrafficBytes() +
                TrafficStats.getTotalTxBytes().validTrafficBytes()

        /** Matches the compact binary-unit format used by the reference app. */
        fun format(context: Context, bytes: Long): String =
            format(bytes) { resource, value -> context.getString(resource, value) }

        internal fun format(bytes: Long, formatValue: (Int, Number) -> String): String {
            val safeBytes = bytes.coerceAtLeast(0L)
            return when {
                safeBytes < KIB -> formatValue(R.string.string_size_bytes, safeBytes)
                safeBytes < MIB -> formatValue(R.string.string_size_kilobytes, safeBytes / KIB)
                safeBytes < GIB -> formatValue(R.string.string_size_megabytes, safeBytes / MIB)
                else -> formatValue(R.string.string_size_gigabytes, safeBytes / GIB)
            }
        }

        private fun Long.validTrafficBytes(): Long = if (this == TrafficStats.UNSUPPORTED.toLong()) 0L else coerceAtLeast(0L)
    }
}
