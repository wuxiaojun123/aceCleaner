package com.nice.aceclean.util

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import java.util.Calendar

data class NetworkUsageSnapshot(
    val mobileBytes: Long,
    val wifiBytes: Long,
    val bytesByUid: Map<Int, Long>,
    val dailyBytes: List<Long>,
) {
    val totalBytes: Long get() = mobileBytes + wifiBytes
}

object NetworkUsageRepository {

    fun currentMonth(context: Context): NetworkUsageSnapshot {
        val now = System.currentTimeMillis()
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val mobile = query(manager, ConnectivityManager.TYPE_MOBILE, monthStart, now)
        val wifi = query(manager, ConnectivityManager.TYPE_WIFI, monthStart, now)
        val byUid = mutableMapOf<Int, Long>()
        listOf(mobile.second, wifi.second).forEach { values ->
            values.forEach { (uid, bytes) -> byUid[uid] = byUid.getOrDefault(uid, 0L) + bytes }
        }
        return NetworkUsageSnapshot(
            mobileBytes = mobile.first,
            wifiBytes = wifi.first,
            bytesByUid = byUid,
            dailyBytes = lastSevenDays(manager, now),
        )
    }

    private fun lastSevenDays(manager: NetworkStatsManager, now: Long): List<Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }
        return List(7) {
            val start = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val end = if (it == 6) now else calendar.timeInMillis
            query(manager, ConnectivityManager.TYPE_MOBILE, start, end).first +
                query(manager, ConnectivityManager.TYPE_WIFI, start, end).first
        }
    }

    private fun query(
        manager: NetworkStatsManager,
        networkType: Int,
        start: Long,
        end: Long,
    ): Pair<Long, Map<Int, Long>> = runCatching {
        val byUid = mutableMapOf<Int, Long>()
        var total = 0L
        manager.querySummary(networkType, null, start, end).use { stats ->
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val bytes = (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
                total += bytes
                byUid[bucket.uid] = byUid.getOrDefault(bucket.uid, 0L) + bytes
            }
        }
        total to byUid
    }.getOrDefault(0L to emptyMap())
}
