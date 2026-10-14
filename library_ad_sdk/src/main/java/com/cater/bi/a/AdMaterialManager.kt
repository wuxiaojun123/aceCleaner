package com.cater.bi.a

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Independent manager for ad material states and instances
 * 
 * created on 2025/8/10
 * @author holmes
 */
object AdMaterialManager {
    
    private val adInstances = ConcurrentHashMap<String, AdBase>()
    private val adStates = ConcurrentHashMap<String, AtomicReference<String>>()
    private val adMetadata = ConcurrentHashMap<String, AdMetadata>()
    
    /**
     * Register ad material with metadata
     */
    fun registerMaterial(placementName: String, metadata: AdMetadata) {
        adMetadata[placementName] = metadata
        adStates[placementName] = AtomicReference(AdState.IDLE)
    }
    
    /**
     * Register ad material from placement and platform
     */
    fun registerMaterial(placement: AdPlacement, platform: AdPlatform) {
        val metadata = AdMetadata(placement, platform)
        registerMaterial(placement.id, metadata)
    }
    
    /**
     * Set ad instance for placement
     */
    fun setAdInstance(placementName: String, ad: AdBase) {
        val previous = adInstances.put(placementName, ad)
        if (previous != null && previous !== ad) {
            runCatching { previous.destroy() }
        }
    }
    
    /**
     * Get ad instance for placement
     */
    fun getAdInstance(placementName: String): AdBase? {
        return adInstances[placementName]
    }
    
    /**
     * Remove ad instance for placement
     */
    fun removeAdInstance(placementName: String): AdBase? {
        return adInstances.remove(placementName)
    }

    /**
     * Get ad metadata for placement
     */
    fun getMetadata(placementName: String): AdMetadata? {
        return adMetadata[placementName]
    }
    
    /**
     * Set ad state for placement
     */
    fun setState(placementName: String, state: String) {
        adStates[placementName]?.set(state)
    }
    
    /**
     * Get ad state for placement
     */
    fun getState(placementName: String): String {
        return adStates[placementName]?.get() ?: AdState.IDLE
    }
    
    /**
     * Check if ad is ready
     */
    fun isReady(placementName: String): Boolean {
        return getState(placementName) == AdState.LOADED
    }
    
    /**
     * Check if placement is registered
     */
    fun isRegistered(placementName: String): Boolean {
        return adMetadata.containsKey(placementName)
    }
    
    /**
     * Get all registered placements
     */
    fun getRegisteredPlacements(): Set<String> {
        return adMetadata.keys.toSet()
    }
    
    /**
     * Clear all materials
     */
    fun clearAll() {
        // don't clear loaded ads
//        adInstances.values.forEach { it.destroy() }
//        adInstances.clear()
//        adStates.clear()

        adMetadata.clear()
    }
    
    /**
     * Get ad statistics
     */
    fun getAdStats(placementName: String): AdStats? {
        val ad = getAdInstance(placementName)
        val metadata = getMetadata(placementName)
        val state = getState(placementName)
        
        return if (metadata != null) {
            AdStats(
                placementName = placementName,
                adType = metadata.placement.type,
                platform = metadata.platform.name,
                state = state,
                ecpm = ad?.getEcpm() ?: 0.0,
                isReady = isReady(placementName),
                unitId = metadata.getPrimaryUnitId()
            )
        } else null
    }
}

/**
 * Ad statistics data class
 */
data class AdStats(
    val placementName: String,
    val adType: String,
    val platform: String,
    val state: String,
    val ecpm: Double,
    val isReady: Boolean,
    val unitId: String
)
