package com.cater.bi.a

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Platform initialization waiter similar to AdHelper.waitSdkOk
 * 
 * created on 2025/8/10
 * @author holmes
 */
object AdPlatformWaiter {
    
    /**
     * Wait for platform initialization with timeout
     * Similar to AdHelper.waitSdkOk implementation
     */
    suspend fun waitPlatformReady(platformName: String, timeout: Long = 10_000L): Boolean {
        val platform = AdPlatformManager.getPlatform(platformName)
        if (platform == null) {
            return false
        }
        
        // If already initialized, return immediately
        if (platform.isInitedSuccess()) {
            return true
        }
        
        // Wait for initialization with timeout
        return try {
            withTimeout(timeout) {
                platform.getInitFlow().collect { isInitialized ->
                    if (isInitialized) {
                        throw CancellationException("Platform initialized")
                    }
                }
            }
            false // Should not reach here
        } catch (e: CancellationException) {
            // Platform initialized successfully
            true
        } catch (e: TimeoutCancellationException) {
            // Timeout waiting for platform initialization
            false
        } catch (e: Exception) {
            // Other errors
            false
        }
    }
    
    /**
     * Wait for any platform to be ready (useful when platform is determined at runtime)
     */
    suspend fun waitAnyPlatformReady(platformNames: List<String>, timeout: Long = 10_000L): String? {
        for (platformName in platformNames) {
            if (waitPlatformReady(platformName, timeout)) {
                return platformName
            }
        }
        return null
    }
}
