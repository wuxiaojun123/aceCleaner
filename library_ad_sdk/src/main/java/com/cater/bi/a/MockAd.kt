package com.cater.bi.a

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Mock ad implementation for testing
 * 
 * created on 2025/8/10
 * @author holmes
 */
class MockAd(
    metadata: AdMetadata
) : AbstractAd(metadata) {
    
//    private val handler = Handler(Looper.getMainLooper())
    
    override fun getPlatform(): String = "Mock"
    
    override fun loadAd(context: Context) {
        // Simulate loading delay
        handler.postDelayed({
            if (state == AdState.LOADING) {
                materialEcpm = (10..100).random().toDouble()
                onLoadSuccess()
            }
        }, (500..2000).random().toLong())
    }
    
    override fun showAd(context: Context, container: AdContainer?) {
        // Simulate show success
        handler.post {
            if (state == AdState.SHOWING) {
                onShowSuccess()
            }
        }
    }
    
    override fun destroyAd() {
        handler.removeCallbacksAndMessages(null)
    }
}
