package com.nice.aceclean.util

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL

data class NetworkTestResult(
    val pingMillis: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
)

object NetworkSpeedTest {
    private const val TEST_HOST = "https://speed.cloudflare.com"
    private const val DOWNLOAD_BYTES = 2_500_000
    private const val UPLOAD_BYTES = 512_000
    private const val CONNECT_TIMEOUT_MILLIS = 8_000
    private const val READ_TIMEOUT_MILLIS = 12_000

    fun measurePing(): Long {
        val samples = buildList {
            repeat(3) {
                val started = SystemClock.elapsedRealtimeNanos()
                openConnection("$TEST_HOST/__down?bytes=0&cache=${System.nanoTime()}").useConnection { connection ->
                    connection.inputStream.use { it.read() }
                }
                add((SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L)
            }
        }
        return samples.sorted()[samples.size / 2]
    }

    fun measureDownloadMbps(): Double {
        var received = 0L
        val started = SystemClock.elapsedRealtimeNanos()
        openConnection("$TEST_HOST/__down?bytes=$DOWNLOAD_BYTES&cache=${System.nanoTime()}").useConnection { connection ->
            connection.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    received += count
                }
            }
        }
        return megabitsPerSecond(received, SystemClock.elapsedRealtimeNanos() - started)
    }

    fun measureUploadMbps(): Double {
        val connection = openConnection("$TEST_HOST/__up").apply {
            requestMethod = "POST"
            doOutput = true
            setFixedLengthStreamingMode(UPLOAD_BYTES)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE) { index -> (index % 251).toByte() }
        val started = SystemClock.elapsedRealtimeNanos()
        connection.useConnection {
            connection.outputStream.use { output ->
                var remaining = UPLOAD_BYTES
                while (remaining > 0) {
                    val count = minOf(remaining, chunk.size)
                    output.write(chunk, 0, count)
                    remaining -= count
                }
                output.flush()
            }
            connection.inputStream.use { it.read() }
        }
        return megabitsPerSecond(UPLOAD_BYTES.toLong(), SystemClock.elapsedRealtimeNanos() - started)
    }

    internal fun megabitsPerSecond(bytes: Long, elapsedNanos: Long): Double {
        if (bytes <= 0L || elapsedNanos <= 0L) return 0.0
        return bytes * 8.0 * 1_000.0 / elapsedNanos
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "SmartCleaner-NetworkTest/1.0")
        }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}
