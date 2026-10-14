package com.cater.bi.a

/**
 * Configuration manager for parsing and managing ad configurations
 * 
 * created on 2025/8/10
 * @author holmes
 */
object AdConfigurationManager {
    
    private var currentConfiguration: AdConfiguration? = null
    
    /**
     * Set the current ad configuration and clear old placements
     */
    fun setConfiguration(configuration: AdConfiguration) {
        // Clear old configuration and placements
        clearConfiguration()
        
        // Set new configuration
        currentConfiguration = configuration
        
        // Register all placements immediately
        registerAllPlacements()
    }
    
    /**
     * Clear current configuration and all registered placements
     */
    fun clearConfiguration() {
        currentConfiguration?.let {
            // Clear all existing placements from material manager
            AdMaterialManager.clearAll()
        }
        currentConfiguration = null
    }
    
    /**
     * Get the current platform configuration
     */
    fun getPlatform(): AdPlatform? {
        return currentConfiguration?.platform
    }
    
    /**
     * Get placement by ID
     */
    fun getPlacement(placementId: String): AdPlacement? {
        return currentConfiguration?.getPlacement(placementId)
    }
    
    /**
     * Get all placements
     */
    fun getAllPlacements(): List<AdPlacement> {
        return currentConfiguration?.placement ?: emptyList()
    }
    
    /**
     * Create metadata for a placement
     */
    fun createMetadata(placementId: String): AdMetadata? {
        val platform = getPlatform() ?: return null
        val placement = getPlacement(placementId) ?: return null
        return AdMetadata(placement, platform)
    }
    
    /**
     * Register all placements from current configuration (without callback)
     */
    fun registerAllPlacements() {
        val platform = getPlatform() ?: return
        
        getAllPlacements().forEach { placement ->
            try {
                AdMaterialManager.registerMaterial(placement, platform)
            } catch (e: Exception) {
                // Log error but continue with other placements
                // Could add logging here if needed
            }
        }
    }
    
    /**
     * Register all placements from current configuration (with callback)
     */
    fun registerAllPlacements(callback: AdRegistrationCallback? = null) {
        val platform = getPlatform()
        if (platform == null) {
            callback?.onFailure("", AdErrorC(AdErrorC.ERROR_INVALID_REQUEST, "No platform configured"))
            return
        }
        
        getAllPlacements().forEach { placement ->
            try {
                AdMaterialManager.registerMaterial(placement, platform)
                callback?.onSuccess(placement.id)
            } catch (e: Exception) {
                val error = AdErrorC(AdErrorC.ERROR_INTERNAL, "Failed to register ${placement.id}: ${e.message}", e)
                callback?.onFailure(placement.id, error)
            }
        }
    }
    
    /**
     * Check if configuration is valid
     */
    fun isConfigurationValid(): Boolean {
        val config = currentConfiguration ?: return false
        return config.platform.name.isNotEmpty() && config.placement.isNotEmpty()
    }
}
