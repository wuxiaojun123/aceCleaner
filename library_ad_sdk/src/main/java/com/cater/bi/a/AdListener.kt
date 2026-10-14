package com.cater.bi.a

/**
 * Ad loading listener
 * 
 * created on 2025/8/10
 * @author holmes
 */
interface AdLoadListener {
    fun onAdLoaded(ad: AdBase)
    fun onAdFailedToLoad(ad: AdBase, error: AdErrorC)
}

/**
 * Ad showing listener
 */
interface AdShowListener {
    fun onAdShown(ad: AdBase)
    fun onAdFailedToShow(ad: AdBase, error: AdErrorC)
    fun onAdClicked(ad: AdBase)
    fun onAdClosed(ad: AdBase)
    fun onAdRewarded(ad: AdBase)
}

/**
 * Ad error information
 */
data class AdErrorC(
    val code: String,
    val message: String,
    val cause: Throwable? = null
) {
    companion object {
        const val ERROR_NO_FILL = "1001"
        const val ERROR_NETWORK = "1002"
        const val ERROR_INVALID_REQUEST = "1003"
        const val ERROR_INTERNAL = "1004"
        const val ERROR_NOT_READY = "1005"
        const val ERROR_TIMEOUT = "1006"
        const val ERROR_LIFECYCLE = "1007"
    }

    /**
     * [AdBase]
     */
    var adObj: Any? = null
}

class AdException(val error: AdErrorC) : Exception(error.message)