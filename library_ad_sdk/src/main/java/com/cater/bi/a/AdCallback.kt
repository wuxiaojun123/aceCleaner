package com.cater.bi.a

import com.nice.library_ad_sdk.util.LogUtils

/**
 * Callback interfaces for ad operations with failure handling
 * 
 * created on 2025/8/10
 * @author holmes
 */

/**
 * Callback for ad registration operations
 */
interface AdRegistrationCallback {
    fun onSuccess(placementName: String)
    fun onFailure(placementName: String, error: AdErrorC)
}

/**
 * Callback for ad loading operations
 */
interface AdLoadCallback {
    fun onSuccess(placementName: String, ad: AdBase)
    fun onFailure(placementName: String, error: AdErrorC)
}

abstract class AdLoadCallbackWrap(val down: AdLoadCallback?): AdLoadCallback {

    override fun onSuccess(placementName: String, ad: AdBase) {
        down?.onSuccess(placementName, ad)
    }

    override fun onFailure(placementName: String, error: AdErrorC) {
        down?.onFailure(placementName, error)
    }
}

open class AdLoadCallbackAdapter: AdLoadCallback {
    override fun onSuccess(placementName: String, ad: AdBase) {
    }

    override fun onFailure(placementName: String, error: AdErrorC) {
    }
}

/**
 * Callback for ad showing operations
 */
interface AdShowCallback {
    fun onSuccess(placementName: String, ad: AdBase)
    fun onFailure(placementName: String, error: AdErrorC)
    fun onClicked(placementName: String, ad: AdBase)
    fun onClosed(placementName: String, ad: AdBase)
    fun onRewarded(placementName: String, ad: AdBase)
}

abstract class AdShowCallbackWrap(val down: AdShowCallback?): AdShowCallback {

    override fun onSuccess(placementName: String, ad: AdBase) {
        down?.onSuccess(placementName, ad)
    }

    override fun onFailure(placementName: String, error: AdErrorC) {
        down?.onFailure(placementName, error)
    }

    override fun onClicked(placementName: String, ad: AdBase) {
        down?.onClicked(placementName, ad)
    }

    override fun onClosed(placementName: String, ad: AdBase) {
        down?.onClosed(placementName, ad)
    }

    override fun onRewarded(placementName: String, ad: AdBase) {
        down?.onRewarded(placementName, ad)
    }
}

open class AdShowCallbackAdapter: AdShowCallback {
    override fun onSuccess(placementName: String, ad: AdBase) {
    }

    override fun onFailure(placementName: String, error: AdErrorC) {
    }

    override fun onClicked(placementName: String, ad: AdBase) {
    }

    override fun onClosed(placementName: String, ad: AdBase) {
    }

    override fun onRewarded(placementName: String, ad: AdBase) {
    }
}

// === Logs ===
class LogLoadCallback(down: AdLoadCallback?) : AdLoadCallbackWrap(down) {
    override fun onSuccess(placementName: String, ad: AdBase) {
        LogUtils.d(tag = "adm", msg = "load success: $placementName")
        super.onSuccess(placementName, ad)
    }

    override fun onFailure(placementName: String, error: AdErrorC) {
        LogUtils.w(tag = "adm", msg = "load failed: $placementName, ${error.message} (${error.code})")
        super.onFailure(placementName, error)
    }
}

class LogShowCallback(down: AdShowCallback?) : AdShowCallbackWrap(down) {
    override fun onSuccess(placementName: String, ad: AdBase) {
        LogUtils.d(tag = "adm", msg = "show success: $placementName, [${ad.getEcpm()}]")
        super.onSuccess(placementName, ad)
    }

    override fun onFailure(placementName: String, error: AdErrorC) {
        LogUtils.w(tag = "adm", msg = "show failed: $placementName, ${error.message} (${error.code})")
        super.onFailure(placementName, error)
    }

    override fun onClicked(placementName: String, ad: AdBase) {
        LogUtils.d(tag = "adm", msg = "clicked: $placementName")
        super.onClicked(placementName, ad)
    }

    override fun onClosed(placementName: String, ad: AdBase) {
        LogUtils.d(tag = "adm", msg = "closed: $placementName")
        super.onClosed(placementName, ad)
    }

    override fun onRewarded(placementName: String, ad: AdBase) {
        LogUtils.d(tag = "adm", msg = "rewarded: $placementName")
        super.onRewarded(placementName, ad)
    }
}
