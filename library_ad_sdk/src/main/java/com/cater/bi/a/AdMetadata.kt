package com.cater.bi.a

/**
 * Simplified ad metadata for direct configuration parsing
 * 
 * created on 2025/8/10
 * @author holmes
 */
data class AdMetadata(
    val placement: AdPlacement,
    val platform: AdPlatform
) {
    /**
     * Get the primary unit ID from placement
     */
    fun getPrimaryUnitId(): String {
        return placement.getPrimaryUnit()?.uid ?: ""
    }
    
    /**
     * Get placement extras with type casting
     */
    inline fun <reified T> getExtra(key: String): T? {
        return placement.getExtra<T>(key)
    }
    
    /**
     * Get placement extras with default value
     */
    inline fun <reified T> getExtra(key: String, defaultValue: T): T {
        return placement.getExtra(key, defaultValue)
    }
}
