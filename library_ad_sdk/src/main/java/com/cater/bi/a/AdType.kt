package com.cater.bi.a

/**
 * Ad types supported by the system
 * 
 * created on 2025/8/10
 * @author holmes
 */
object AdType {
    const val BANNER = "banner"
    const val INTERSTITIAL = "interstitial"
    const val NATIVE = "native"
    const val REWARDED = "reward"
    const val SPLASH = "splash"
}

/**
 * Ad platforms supported by the system
 */
object AdPlatforms {
    const val ADMOB = "admob"
    const val TOPON = "topon"
    const val MOCK = "mock"
}

/**
 * Ad loading and showing states
 */
object AdState {
    const val IDLE = "idle"
    const val LOADING = "loading"
    const val LOADED = "loaded"
    const val SHOWING = "showing"
    const val SHOWN = "shown"
    const val FAILED = "failed"
    const val DESTROYED = "destroyed"
}
