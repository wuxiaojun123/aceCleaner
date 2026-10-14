package com.cater.bi.a

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Platform manager for handling platform registration and initialization
 * 
 * created on 2025/8/10
 * @author holmes
 */
object AdPlatformManager {
    
    private val platforms = ConcurrentHashMap<String, AdPlatformMediation>()

    val scope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
    
    init {
        // Register default platforms
        registerPlatform(MockPlatformMediation)
        registerPlatform(AdMobPlatformMediation)
        registerPlatform(TopOnPlatformMediation)
    }
    
    /**
     * Register a platform mediation
     */
    fun registerPlatform(platform: AdPlatformMediation) {
        platforms[platform.getPlatformType()] = platform
    }
    
    /**
     * Get platform by name
     */
    fun getPlatform(platformName: String): AdPlatformMediation? {
        return platforms[platformName]
    }
    
    /**
     * Initialize platform with configuration
     */
    fun initializePlatform(
        context: Context,
        platform: AdPlatform,
        callback: AdPlatformInitCallback? = null,
        extraBundle: Map<String, String> = emptyMap()
    ) {
        val mediation = getPlatform(platform.name)
        if (mediation == null) {
            val error = AdErrorC(AdErrorC.ERROR_INVALID_REQUEST, "Platform '${platform.name}' not registered")
            callback?.onFailure(platform.name, error)
            return
        }
        
        try {
            // Convert platform extras to string map
            val extras = mutableMapOf<String, String>()
            // Add any additional configuration from platform
            if (!extraBundle.isNullOrEmpty()) {
                extras.putAll(extraBundle)
            }
            
            mediation.init(context, platform.appid, platform.appkey, extras)

            scope.launch {
                runCatching {
                    mediation.getInitFlow().collect {
                        // Check initialization success
                        if (mediation.isInitedSuccess()) {
                            callback?.onSuccess(platform.name)
                        } else {
                            // For async initialization, you might want to observe the init flow
                            val error = AdErrorC(AdErrorC.ERROR_INTERNAL, "Platform initialization failed")
                            callback?.onFailure(platform.name, error)
                        }
                        throw CancellationException("done")
                    }
                }
            }
        } catch (e: Exception) {
            val error = AdErrorC(AdErrorC.ERROR_INTERNAL, "Platform init error: ${e.message}", e)
            callback?.onFailure(platform.name, error)
        }
    }
    
    /**
     * Create ad instance using platform mediation
     */
    fun createAd(metadata: AdMetadata): AdBase? {
        val platform = getPlatform(metadata.platform.name)
        return platform?.createAd(metadata)
    }
    
    /**
     * Check if platform is initialized
     */
    fun isPlatformInitialized(platformName: String): Boolean {
        return getPlatform(platformName)?.isInitedSuccess() ?: false
    }
    
    /**
     * Get all registered platforms
     */
    fun getRegisteredPlatforms(): Set<String> {
        return platforms.keys.toSet()
    }
    
    /**
     * Get platform configuration
     */
    fun getPlatformConfig(platformName: String): Map<String, Any>? {
        return getPlatform(platformName)?.getPlatformConfig()
    }
    
    /**
     * Update platform extras
     */
    fun updatePlatformExtras(platformName: String, extras: Map<String, String>) {
        getPlatform(platformName)?.updateExtras(extras)
    }

    suspend fun beforeLoadAd(context: Context, platformName: String): AdErrorC? {
        return getPlatform(platformName)?.beforeLoadAd(context)
    }
    
    /**
     * Dump revenue from ad
     */
    fun dumpRevenue(ad: AdBase): Any? {
        val platform = getPlatform(ad.metadata.platform.name)
        return platform?.dumpRevenue(ad)
    }
}

/**
 * Callback for platform initialization
 */
interface AdPlatformInitCallback {
    fun onSuccess(platformName: String)
    fun onFailure(platformName: String, error: AdErrorC)
}
