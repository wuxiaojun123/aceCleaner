package com.cater.bi.a

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for ad configuration JSON
 * 
 * created on 2025/8/10
 * @author holmes
 */
object AdConfigParser {
    
    /**
     * Parse ad configuration from JSON string
     */
    fun parseFromJson(jsonString: String): AdConfiguration {
        val json = JSONObject(jsonString)
        
        // Parse platform
        val platformJson = json.getJSONObject("platform")
        val platform = AdPlatform(
            name = platformJson.getString("name"),
            appid = platformJson.optString("appid", ""),
            appkey = platformJson.optString("appkey", "")
        )
        
        // Parse placements
        val placementArray = json.getJSONArray("placement")
        val placements = mutableListOf<AdPlacement>()
        
        for (i in 0 until placementArray.length()) {
            val placementJson = placementArray.getJSONObject(i)
            
            // Parse extras
            val extrasJson = placementJson.optJSONObject("extras")
            val extras = mutableMapOf<String, Any>()
            extrasJson?.let { extrasObj ->
                extrasObj.keys().forEach { key ->
                    extras[key] = extrasObj.get(key)
                }
            }
            
            // Parse units
            val unitsArray = placementJson.getJSONArray("units")
            val units = mutableListOf<AdUnit>()
            for (j in 0 until unitsArray.length()) {
                val unitJson = unitsArray.getJSONObject(j)
                units.add(AdUnit(uid = unitJson.getString("uid")))
            }
            
            placements.add(
                AdPlacement(
                    id = placementJson.getString("id"),
                    type = placementJson.getString("type"),
                    extras = extras,
                    units = units
                )
            )
        }
        
        return AdConfiguration(platform, placements)
    }
    
    /**
     * Create sample configuration for testing
     */
    fun createSampleConfiguration(): AdConfiguration {
        val platform = AdPlatform(
            name = AdPlatform.MOCK,
            appid = "test_app_id",
            appkey = "test_app_key"
        )
        
        val placements = listOf(
            AdPlacement(
                id = "main_banner",
                type = AdType.BANNER,
                extras = mapOf("autoLoad" to true, "timeout" to 10000),
                units = listOf(AdUnit("banner_unit_001"))
            ),
            AdPlacement(
                id = "level_complete",
                type = AdType.INTERSTITIAL,
                extras = mapOf("autoLoad" to false),
                units = listOf(AdUnit("interstitial_unit_001"))
            ),
            AdPlacement(
                id = "extra_coins",
                type = AdType.REWARDED,
                extras = emptyMap(),
                units = listOf(AdUnit("rewarded_unit_001"))
            )
        )
        
        return AdConfiguration(platform, placements)
    }
}
