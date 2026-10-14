package com.cater.bi.analyzer

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persisted impression and revenue totals for one ad format. */
data class AdTypeStats(
    val adType: String,
    val dailyImpressionCount: Int = 0,
    val totalImpressionCount: Int = 0,
    val dailyEcpmSum: Double = 0.0,
    val dailyRevenueSum: Double = 0.0,
    val totalEcpmSum: Double = 0.0,
    val totalRevenueSum: Double = 0.0,
    val lastUpdateDate: String = currentDate()
) {
    fun dailyEcpm(): Double = if (dailyImpressionCount > 0) dailyEcpmSum / dailyImpressionCount else 0.0

    fun totalEcpm(): Double = if (totalImpressionCount > 0) totalEcpmSum / totalImpressionCount else 0.0

    fun needsDailyReset(): Boolean = lastUpdateDate != currentDate()

    fun resetDaily(): AdTypeStats = copy(
        dailyImpressionCount = 0,
        dailyEcpmSum = 0.0,
        dailyRevenueSum = 0.0,
        lastUpdateDate = currentDate()
    )

    fun recordImpression(ecpm: Double, revenue: Double): AdTypeStats = copy(
        dailyImpressionCount = dailyImpressionCount + 1,
        totalImpressionCount = totalImpressionCount + 1,
        dailyEcpmSum = dailyEcpmSum + ecpm,
        totalEcpmSum = totalEcpmSum + ecpm,
        dailyRevenueSum = dailyRevenueSum + revenue,
        totalRevenueSum = totalRevenueSum + revenue,
        lastUpdateDate = currentDate()
    )

    fun toJson(): String = JSONObject().apply {
        put("ad_type", adType)
        put("daily_impression_count", dailyImpressionCount)
        put("total_impression_count", totalImpressionCount)
        put("daily_ecpm_sum", dailyEcpmSum)
        put("daily_revenue_sum", dailyRevenueSum)
        put("total_ecpm_sum", totalEcpmSum)
        put("total_revenue_sum", totalRevenueSum)
        put("last_update_date", lastUpdateDate)
    }.toString()

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun currentDate(): String = dateFormat.format(Date())

        fun fromJson(value: String): AdTypeStats? = runCatching {
            JSONObject(value).let { json ->
                AdTypeStats(
                    adType = json.getString("ad_type"),
                    dailyImpressionCount = json.optInt("daily_impression_count"),
                    totalImpressionCount = json.optInt("total_impression_count"),
                    dailyEcpmSum = json.optDouble("daily_ecpm_sum"),
                    dailyRevenueSum = json.optDouble("daily_revenue_sum"),
                    totalEcpmSum = json.optDouble("total_ecpm_sum"),
                    totalRevenueSum = json.optDouble("total_revenue_sum"),
                    lastUpdateDate = json.optString("last_update_date", currentDate())
                )
            }
        }.getOrNull()
    }
}
