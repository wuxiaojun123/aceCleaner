package com.cater.bi.a

/**
 * Factory for creating ad instances using platform mediation
 * 
 * created on 2025/8/10
 * @author holmes
 */
object AdFactory {
    
    /**
     * Create ad instance from metadata using platform manager
     */
    fun createAd(metadata: AdMetadata): AdBase {
        val ad = AdPlatformManager.createAd(metadata)
        return ad ?: throw IllegalArgumentException(
            "Failed to create ad for platform: ${metadata.platform.name}, type: ${metadata.placement.type}"
        )
    }
    
    /**
     * Check if platform supports ad type
     */
    fun isPlatformSupported(platformName: String): Boolean {
        return AdPlatformManager.getPlatform(platformName) != null
    }
    
    /**
     * Get supported platforms
     */
    fun getSupportedPlatforms(): Set<String> {
        return AdPlatformManager.getRegisteredPlatforms()
    }
}
