package com.nice.library_float

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.math.max


object NetworkSpeedTester {
    data class DownloadProgress(
        val instantaneousMbps: Double,
        val averageMbps: Double,
        val elapsedMs: Long,
    )

    data class DownloadResult(
        val instantaneousMbps: Double,
        val averageMbps: Double,
    )

    fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    
    suspend fun runDownloadTest(
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        LogUtils.d(
            "download test connect: url=$DOWNLOAD_URL, connectTimeout=${CONNECT_TIMEOUT_MS}ms, readTimeout=${READ_TIMEOUT_MS}ms",
            TAG,
        )
        val connection = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            useCaches = false
        }
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) connection.disconnect()
        }

        try {
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesReadTotal = 0L
            var bytesSinceLastSample = 0L
            val startAt = SystemClock.elapsedRealtime()
            var lastSampleAt = startAt
            var lastLogAt = startAt
            var latestInstantaneousMbps = 0.0

            BufferedInputStream(connection.inputStream).use { input ->
                LogUtils.d(
                    "download response: code=${connection.responseCode}, contentLength=${connection.contentLengthLong}",
                    TAG,
                )
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break

                    bytesReadTotal += read
                    bytesSinceLastSample += read
                    val now = SystemClock.elapsedRealtime()
                    val elapsedMs = max(1L, now - startAt)
                    val sampleElapsedMs = now - lastSampleAt

                    if (sampleElapsedMs >= PROGRESS_UPDATE_INTERVAL_MS) {
                        latestInstantaneousMbps = mbps(bytesSinceLastSample, sampleElapsedMs)
                        onProgress(
                            DownloadProgress(
                                instantaneousMbps = latestInstantaneousMbps,
                                averageMbps = mbps(bytesReadTotal, elapsedMs),
                                elapsedMs = elapsedMs,
                            ),
                        )
                        bytesSinceLastSample = 0L
                        lastSampleAt = now
                    }
                    if (now - lastLogAt >= PROGRESS_LOG_INTERVAL_MS) {
                        LogUtils.d(
                            "download progress: bytes=$bytesReadTotal, elapsed=${elapsedMs}ms, speed=${formatSpeedForLog(latestInstantaneousMbps)} Mbps",
                            TAG,
                        )
                        lastLogAt = now
                    }
                    if (elapsedMs >= DOWNLOAD_DURATION_MS) break
                }
            }

            val totalElapsedMs = max(1L, SystemClock.elapsedRealtime() - startAt)
            if (bytesSinceLastSample > 0L) {
                latestInstantaneousMbps = mbps(
                    bytesSinceLastSample,
                    max(1L, totalElapsedMs - lastSampleAt + startAt),
                )
            }
            val averageMbps = mbps(bytesReadTotal, totalElapsedMs)
            LogUtils.d(
                "download finish: bytes=$bytesReadTotal, elapsed=${totalElapsedMs}ms, instant=${formatSpeedForLog(latestInstantaneousMbps)} Mbps, average=${formatSpeedForLog(averageMbps)} Mbps",
                TAG,
            )
            DownloadResult(latestInstantaneousMbps, averageMbps)
        } catch (t: Throwable) {
            LogUtils.e("download failed: ${t.javaClass.simpleName}: ${t.message}", TAG, t)
            throw t
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
            LogUtils.d("download connection disconnected", TAG)
        }
    }

    
    suspend fun runUploadTest(): Double = withContext(Dispatchers.IO) {
        val connection = (URL(UPLOAD_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = UPLOAD_RESPONSE_TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/octet-stream")
            setChunkedStreamingMode(UPLOAD_BUFFER_SIZE)
        }
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) connection.disconnect()
        }
        val buffer = ByteArray(UPLOAD_BUFFER_SIZE) { index -> (index % 251).toByte() }
        var bytesWrittenTotal = 0L
        val startAt = SystemClock.elapsedRealtime()
        var uploadBodyElapsedMs = 0L

        try {
            BufferedOutputStream(connection.outputStream).use { output ->
                while (bytesWrittenTotal < UPLOAD_BYTES) {
                    coroutineContext.ensureActive()
                    val writeCount = minOf(
                        buffer.size.toLong(),
                        UPLOAD_BYTES - bytesWrittenTotal,
                    ).toInt()
                    output.write(buffer, 0, writeCount)
                    bytesWrittenTotal += writeCount
                    if (SystemClock.elapsedRealtime() - startAt >= UPLOAD_DURATION_MS) break
                }
                output.flush()
                uploadBodyElapsedMs = max(1L, SystemClock.elapsedRealtime() - startAt)
            }

            val speedMbps = mbps(bytesWrittenTotal, uploadBodyElapsedMs)

            connection.responseCode
            speedMbps
        } catch (e: SocketTimeoutException) {
            if (bytesWrittenTotal <= 0L) throw e
            val elapsedMs = max(
                1L,
                uploadBodyElapsedMs.takeIf { it > 0L }
                    ?: (SystemClock.elapsedRealtime() - startAt),
            )
            mbps(bytesWrittenTotal, elapsedMs)
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    fun downloadProgressPercent(elapsedMs: Long): Float {
        return (elapsedMs.toFloat() / DOWNLOAD_DURATION_MS * DOWNLOAD_PROGRESS_MAX).coerceIn(
            DOWNLOAD_PROGRESS_MIN,
            DOWNLOAD_PROGRESS_MAX,
        )
    }

    private fun mbps(bytes: Long, millis: Long): Double {
        return bytes * BITS_PER_BYTE / (millis / 1_000.0) / BYTES_PER_MEGABIT
    }

    private fun formatSpeedForLog(speedMbps: Double): String = "%.2f".format(speedMbps)

    private const val DOWNLOAD_BYTES = 25_000_000
    private const val DOWNLOAD_DURATION_MS = 8_000L
    private const val UPLOAD_BYTES = 1_000_000L
    private const val UPLOAD_DURATION_MS = 3_000L
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 6_000
    private const val UPLOAD_RESPONSE_TIMEOUT_MS = 1_000
    private const val BUFFER_SIZE = 64 * 1024
    private const val UPLOAD_BUFFER_SIZE = 8 * 1024
    private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
    private const val PROGRESS_LOG_INTERVAL_MS = 1_000L
    private const val DOWNLOAD_PROGRESS_MIN = 4f
    private const val DOWNLOAD_PROGRESS_MAX = 50f
    private const val BITS_PER_BYTE = 8.0
    private const val BYTES_PER_MEGABIT = 1_000_000.0
    private const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=$DOWNLOAD_BYTES"
    private const val UPLOAD_URL = "https://speed.cloudflare.com/__up"
    private const val TAG = "NetworkSpeed"
}
