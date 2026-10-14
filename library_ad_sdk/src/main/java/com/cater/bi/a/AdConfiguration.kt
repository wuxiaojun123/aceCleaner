package com.cater.bi.a

/**
 * Complete ad configuration matching the JSON structure
 * 
 * created on 2025/8/10
 * @author holmes
 */
data class AdConfiguration(
    val platform: AdPlatform,
    val placement: List<AdPlacement>
) {
    /**
     * Get placement by ID
     */
    fun getPlacement(id: String): AdPlacement? {
        return placement.find { it.id == id }
    }
    
    /**
     * Get all placements by type
     */
    fun getPlacementsByType(type: String): List<AdPlacement> {
        return placement.filter { it.type == type }
    }
}
