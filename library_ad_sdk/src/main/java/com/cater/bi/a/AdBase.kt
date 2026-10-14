package com.cater.bi.a

import android.content.Context

/**
 * Base interface for all ad types
 * 
 * created on 2025/8/10
 * @author holmes
 */
interface AdBase {
    
    /**
     * Ad metadata containing all configuration
     */
    val metadata: AdMetadata
    
    /**
     * Current ad state
     */
    val state: String

    fun getAdType(): String

    fun getUnitCode(): String
    
    /**
     * Check if ad is ready to show
     */
    fun isReady(): Boolean
    
    /**
     * Load ad with context
     */
    fun load(context: Context, listener: AdLoadListener? = null)
    
    /**
     * Show ad
     */
    fun show(context: Context, container: AdContainer?, render: INativeRender?, listener: AdShowListener? = null)
    
    /**
     * Destroy ad and release resources
     */
    fun destroy()

    fun getSourceNetwork(): String

    /**
     * Get ad platform (admob, topon, etc.)
     */
    fun getPlatform(): String
    
    /**
     * Get eCPM if available
     */
    fun getEcpm(): Double

    fun getRevenue(): AdRevenue?

}
