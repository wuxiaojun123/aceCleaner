package com.cater.bi.analyzer

import android.content.Context
import com.cater.bi.a.AdBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Local, persistent aggregate statistics for ad impressions and revenue. */
object AdAnalyzer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _stats = MutableStateFlow<Map<String, AdTypeStats>>(emptyMap())
    private var initialized = false

    val stats: StateFlow<Map<String, AdTypeStats>> = _stats.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        AdAnalyticsStore.initialize(context)
        scope.launch {
            AdAnalyticsStore.allStats().collect { _stats.value = it }
        }
    }

    fun recordImpression(ad: AdBase) {
        check(initialized) { "AdAnalyzer.initialize() must be called before recording impressions" }
        scope.launch {
            val ecpm = ad.getEcpm()
            val revenue = ad.getRevenue()?.value ?: ecpm / 1_000.0
            AdAnalyticsStore.recordImpression(ad.getAdType(), ecpm, revenue)
        }
    }

    fun getDailyImpressionCount(adType: String): Int = _stats.value[adType]?.dailyImpressionCount ?: 0

    fun getTotalImpressionCount(adType: String): Int = _stats.value[adType]?.totalImpressionCount ?: 0

    fun getDailyEcpm(adType: String): Double = _stats.value[adType]?.dailyEcpm() ?: 0.0

    fun getTotalEcpm(adType: String): Double = _stats.value[adType]?.totalEcpm() ?: 0.0

    fun getAllDailyImpressionCounts(): Map<String, Int> =
        _stats.value.mapValues { it.value.dailyImpressionCount }

    fun getAllTotalImpressionCounts(): Map<String, Int> =
        _stats.value.mapValues { it.value.totalImpressionCount }

    fun getAllDailyEcpm(): Map<String, Double> =
        _stats.value.mapValues { it.value.dailyEcpm() }

    fun getAllTotalEcpm(): Map<String, Double> =
        _stats.value.mapValues { it.value.totalEcpm() }

    fun resetDailyStats() {
        scope.launch { AdAnalyticsStore.resetDailyStats() }
    }

    fun clearAll() {
        scope.launch { AdAnalyticsStore.clearAll() }
    }

    fun getSummaryReport(): String = buildString {
        appendLine("=== AD Analytics Report ===")
        AdAnalyticsStore.adTypes.forEach { adType ->
            appendLine(
                "$adType: daily=${getDailyImpressionCount(adType)}, " +
                    "total=${getTotalImpressionCount(adType)}, " +
                    "eCPM=${"%.2f".format(getTotalEcpm(adType))}"
            )
        }
    }
}
