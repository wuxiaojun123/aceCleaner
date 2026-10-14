package com.cater.bi.a

import android.content.Context
import com.nice.library_ad_sdk.util.LogUtils
import java.util.concurrent.atomic.AtomicReference

/**
 * Abstract implementation of AdBase with common functionality
 *
 * created on 2025/8/10
 * @author holmes
 */
abstract class AbstractAd(
    override val metadata: AdMetadata
) : AdBase {

    companion object {
        const val TAG = "abad"
    }

    private val _state = AtomicReference(AdState.IDLE)
    override val state: String
        get() = _state.get()

    protected var loadListener: AdLoadListener? = null
    protected var showListener: AdShowListener? = null
    protected var materialEcpm: Double = 0.0
    protected var materialRevenue: AdRevenue? = null

    protected var delayCallShowSuccessAfterPaid = false
    protected var callShowDelayInterval = 3000L
    protected var onShownCalled = false
    protected var onDestroyCall = false
    protected var onPaidCalled = false

    protected var destroyOnClose = true
    var loadTimeout = 90_000L
        private set
    var showTimeout = 10_000L
        private set

    protected val handler
        get() = Adm.handler

    private val durations = object {
        var loadStartAt = 0L
        var loadEndAt = 0L
        var showStartAt = 0L
        var showEndAt = 0L
        var clickedCount = 0
        var clickedAt = 0L

        val preformLoadTimeout = object : Runnable {
            private var once = false
            override fun run() {
                if (once) {
                    return
                }
                once = true

                onLoadFailed(AdErrorC(AdErrorC.ERROR_TIMEOUT, "load timeout"))
            }
        }

        val performShowTimeout = object : Runnable {
            private var once = false
            override fun run() {
                if (once) {
                    return
                }
                once = true

                onShowFailed(AdErrorC(AdErrorC.ERROR_TIMEOUT, "show timeout"))
            }
        }
    }

    protected fun setState(newState: String) {
        _state.set(newState)
    }

    override fun getAdType(): String {
        return metadata.placement.type
    }

    override fun getUnitCode(): String {
        return metadata.getPrimaryUnitId()
    }

    fun getLoadDuration(): Long {
        return (durations.loadEndAt - durations.loadStartAt).coerceAtLeast(0L)
    }

    fun getShowDuration(): Long {
        return (durations.showEndAt - durations.showStartAt).coerceAtLeast(0L)
    }

    fun getClickedCount(): Int {
        return durations.clickedCount
    }

    fun getClickedDuration(): Long {
        return (durations.clickedAt - durations.showEndAt).coerceAtLeast(0L)
    }

    override fun isReady(): Boolean {
        return state == AdState.LOADED
    }

    override fun load(context: Context, listener: AdLoadListener?) {
        if (state == AdState.LOADING) {
            return
        }
        durations.loadStartAt = System.currentTimeMillis()

        this.loadListener = listener
        setState(AdState.LOADING)

        handler.removeCallbacks(durations.preformLoadTimeout)
        if (loadTimeout > 0L) {
            handler.postDelayed(durations.preformLoadTimeout, loadTimeout)
        }

        try {
            loadAd(context)
        } catch (e: Exception) {
            onLoadFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Load failed: ${e.message}", e))
        }
    }

    open fun show(context: Context, container: AdContainer?, listener: AdShowListener?) {
        if (!isReady()) {
            listener?.onAdFailedToShow(this, AdErrorC(AdErrorC.ERROR_NOT_READY, "Ad not ready"))
            return
        }

        this.showListener = listener
        setState(AdState.SHOWING)
        durations.showStartAt = System.currentTimeMillis()

        handler.removeCallbacks(durations.performShowTimeout)
        if (showTimeout > 0L) {
            handler.postDelayed(durations.performShowTimeout, showTimeout)
        }

        try {
            showAd(context, container)
        } catch (e: Exception) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Show failed: ${e.message}", e))
        }
    }

    // Native ad show method with render support
    override fun show(
        context: Context,
        container: AdContainer?,
        render: INativeRender?,
        listener: AdShowListener?
    ) {
        if (!isReady()) {
            listener?.onAdFailedToShow(this, AdErrorC(AdErrorC.ERROR_NOT_READY, "Ad not ready"))
            return
        }

        this.showListener = listener
        setState(AdState.SHOWING)
        durations.showStartAt = System.currentTimeMillis()

        try {
            showAd(context, container, render)
        } catch (e: Exception) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Show failed: ${e.message}", e))
        }
    }

    override fun destroy() {
        if (onDestroyCall) {
            return
        }
        onDestroyCall = true
        setState(AdState.DESTROYED)
        loadListener = null
        showListener = null
        LogUtils.d(tag = TAG, msg = "destroy (${getAdType()}) [${getUnitCode()}]")
        destroyAd()
    }

    override fun getSourceNetwork(): String {
        return ""
    }

    override fun getEcpm(): Double = materialEcpm

    override fun getRevenue(): AdRevenue? {
        return materialRevenue
    }

    // Abstract methods for platform-specific implementation
    protected abstract fun loadAd(context: Context)
    protected abstract fun showAd(context: Context, container: AdContainer?)
    protected abstract fun destroyAd()

    // Native ad show method with render support (default implementation for backward compatibility)
    protected open fun showAd(context: Context, container: AdContainer?, render: INativeRender?) {
        showAd(context, container)
    }

    // Callback methods for subclasses
    protected fun onLoadSuccess() {
        handler.removeCallbacks(durations.preformLoadTimeout)
        setState(AdState.LOADED)
        durations.loadEndAt = System.currentTimeMillis()
        loadListener?.onAdLoaded(this)
    }

    protected fun onLoadFailed(error: AdErrorC) {
        handler.removeCallbacks(durations.preformLoadTimeout)
        setState(AdState.FAILED)
        durations.loadEndAt = System.currentTimeMillis()
        loadListener?.onAdFailedToLoad(this, error)
    }

    private var delayForShowSuccessRun: Runnable? = null
    protected fun onShowSuccess() {
        if (onShownCalled) {
            return
        }
        handler.removeCallbacks(durations.performShowTimeout)
        onShownCalled = true
        durations.showEndAt = System.currentTimeMillis()

        if (!delayCallShowSuccessAfterPaid || onPaidCalled) {
            setState(AdState.SHOWN)
            showListener?.onAdShown(this)
        } else {
            val thisAd = this
            val run = object : Runnable {
                private var once = false
                override fun run() {
                    if (once) {
                        return
                    }
                    once = true
                    delayForShowSuccessRun = null
                    Adm.handler.removeCallbacks(this)
                    setState(AdState.SHOWN)
                    showListener?.onAdShown(thisAd)
                }
            }
            delayForShowSuccessRun = run
            Adm.handler.postDelayed(run, callShowDelayInterval)
        }
    }

    protected fun onShowFailed(error: AdErrorC) {
        handler.removeCallbacks(durations.performShowTimeout)
        setState(AdState.FAILED)
        durations.showEndAt = System.currentTimeMillis()
        showListener?.onAdFailedToShow(this, error)
    }

    protected fun onPaid() {
        if (onPaidCalled) {
            return
        }
        onPaidCalled = true
        if (Adm.debug) {
            LogUtils.d(tag = TAG, msg = "onPaid [${getEcpm()}] (${getAdType()}) (${getUnitCode()})")
        }

        if (delayCallShowSuccessAfterPaid) {
            delayForShowSuccessRun?.also {
                Adm.handler.removeCallbacks(it)
                it.run()
            }
        }
    }

    protected fun onAdClicked() {
        durations.clickedAt = System.currentTimeMillis()
        durations.clickedCount++
        showListener?.onAdClicked(this)
    }

    protected fun onAdClosed() {
        showListener?.onAdClosed(this)
        if (destroyOnClose) {
            destroy()
        }
    }

    protected fun onAdRewarded() {
        showListener?.onAdRewarded(this)
    }
}
