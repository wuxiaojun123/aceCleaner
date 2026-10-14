package com.cater.bi.a

/**
 * Ad placement configuration - represents a scenario in the app
 * Conceals real ad units and loading strategy from caller
 * 
 * created on 2025/8/10
 * @author holmes
 */
class AdPlacement(
    val id: String,
    val type: String,
    val extras: Map<String, Any> = emptyMap(),
    val units: List<AdUnit> = emptyList()
) {
    /**
     * Get the primary ad unit (first one)
     */
    fun getPrimaryUnit(): AdUnit? {
        return units.firstOrNull()
    }
    
    /**
     * Get extra parameter with type casting
     */
    inline fun <reified T> getExtra(key: String): T? {
        return extras[key] as? T
    }
    
    /**
     * Get extra parameter with default value
     */
    inline fun <reified T> getExtra(key: String, defaultValue: T): T {
        return getExtra<T>(key) ?: defaultValue
    }
}
