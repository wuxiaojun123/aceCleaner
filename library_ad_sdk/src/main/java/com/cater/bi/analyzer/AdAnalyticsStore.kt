package com.cater.bi.analyzer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cater.bi.a.AdType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore-backed local storage for aggregate ad statistics, not analytics events. */
object AdAnalyticsStore {
    private val Context.adAnalyticsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ad_analytics")
    private lateinit var appContext: Context

    val adTypes = listOf(
        AdType.BANNER,
        AdType.INTERSTITIAL,
        AdType.NATIVE,
        AdType.REWARDED,
        AdType.SPLASH
    )

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun allStats(): Flow<Map<String, AdTypeStats>> = dataStore.data.map { preferences ->
        buildMap {
            adTypes.forEach { adType ->
                preferences[statsKey(adType)]?.let(AdTypeStats::fromJson)?.let { put(adType, it) }
            }
        }
    }

    suspend fun recordImpression(adType: String, ecpm: Double, revenue: Double) {
        dataStore.edit { preferences ->
            val key = statsKey(adType)
            val current = preferences[key]?.let(AdTypeStats::fromJson)
                ?: AdTypeStats(adType = adType)
            val updated = (if (current.needsDailyReset()) current.resetDaily() else current)
                .recordImpression(ecpm, revenue)
            preferences[key] = updated.toJson()
        }
    }

    suspend fun resetDailyStats() {
        dataStore.edit { preferences ->
            adTypes.forEach { adType ->
                val key = statsKey(adType)
                preferences[key]?.let(AdTypeStats::fromJson)?.let { preferences[key] = it.resetDaily().toJson() }
            }
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private val dataStore: DataStore<Preferences>
        get() {
            check(::appContext.isInitialized) { "AdAnalyticsStore.initialize() must be called first" }
            return appContext.adAnalyticsDataStore
        }

    private fun statsKey(adType: String) = stringPreferencesKey("stats_$adType")
}
