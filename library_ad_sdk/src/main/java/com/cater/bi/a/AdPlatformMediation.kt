package com.cater.bi.a

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Abstract ad platform mediation inspired by TpMediation
 * Supports platform initialization and ad creation actions
 * 
 * created on 2025/8/10
 * @author holmes
 */
abstract class AdPlatformMediation {
    
    var initTimeout = 10_000L
    
    protected val initCall = AtomicBoolean()
    protected var tag = "apm"
    
    private val _inited = MutableStateFlow(-1)
    
    /**
     * Get platform type/name
     */
    abstract fun getPlatformType(): String
    
    /**
     * Initialize the platform with credentials and extras
     */
    abstract fun init(context: Context, appId: String, appKey: String, extras: Map<String, String>)

    /**
     * Called before loading an ad, platform can block load by returning an error.
     */
    open suspend fun beforeLoadAd(context: Context): AdErrorC? {
        return null
    }
    
    /**
     * Update platform extras after initialization
     */
    open fun updateExtras(extras: Map<String, String>) {
        // Override in platform implementations if needed
    }
    
    /**
     * Called when platform initialization succeeds
     */
    protected open fun onInitSuccess() {
        if (_inited.value == 1) {
            return
        }
        _inited.value = 1
    }

    fun onInitFailed(msg: String) {
        if (_inited.value == 0) {
            return
        }
        _inited.value = 0
    }
    
    /**
     * Check if platform is initialized successfully
     */
    fun isInitedSuccess(): Boolean = _inited.value == 1
    
    /**
     * Get initialization state flow for observing
     */
    fun getInitFlow(): Flow<Boolean> = _inited
        .filter { it != -1 }
        .map { it == 1 }

    /**
     * @return -1: not initialized
     *         0: initializing
     *         1: initialized
     */
    fun getInitRawFlow(): Flow<Int> = _inited

    /**
     * Create banner ad instance
     */
    open fun createBanner(metadata: AdMetadata): AdBase? {
        return null
    }
    
    /**
     * Create interstitial ad instance
     */
    open fun createInterstitial(metadata: AdMetadata): AdBase? {
        return null
    }
    
    /**
     * Create native ad instance
     */
    open fun createNative(metadata: AdMetadata): AdBase? {
        return null
    }
    
    /**
     * Create rewarded ad instance
     */
    open fun createRewarded(metadata: AdMetadata): AdBase? {
        return null
    }
    
    /**
     * Create splash ad instance
     */
    open fun createSplash(metadata: AdMetadata): AdBase? {
        return null
    }
    
    /**
     * Create ad instance based on type
     */
    fun createAd(metadata: AdMetadata): AdBase? {
        return when (metadata.placement.type) {
            AdType.BANNER -> createBanner(metadata)
            AdType.INTERSTITIAL -> createInterstitial(metadata)
            AdType.NATIVE -> createNative(metadata)
            AdType.REWARDED -> createRewarded(metadata)
            AdType.SPLASH -> createSplash(metadata)
            else -> null
        }
    }
    
    /**
     * Dump revenue information from ad
     */
    open fun dumpRevenue(ad: AdBase): Any? {
        return null
    }
    
    /**
     * Get platform configuration
     */
    open fun getPlatformConfig(): Map<String, Any> {
        return emptyMap()
    }
}

/**
 * Mock platform implementation for testing
 */
object MockPlatformMediation : AdPlatformMediation() {
    
    override fun getPlatformType(): String {
        return AdPlatforms.MOCK
    }
    
    override fun init(context: Context, appId: String, appKey: String, extras: Map<String, String>) {
        // Simulate initialization success
        onInitSuccess()
    }
    
    override fun createBanner(metadata: AdMetadata): AdBase? {
        return MockAd(metadata)
    }
    
    override fun createInterstitial(metadata: AdMetadata): AdBase? {
        return MockAd(metadata)
    }
    
    override fun createNative(metadata: AdMetadata): AdBase? {
        return MockAd(metadata)
    }
    
    override fun createRewarded(metadata: AdMetadata): AdBase? {
        return MockAd(metadata)
    }
    
    override fun createSplash(metadata: AdMetadata): AdBase? {
        return MockAd(metadata)
    }
}
