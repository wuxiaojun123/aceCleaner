package com.nice.library_ad_sdk

import android.content.Context
import android.view.ViewGroup
import androidx.annotation.RawRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.cater.bi.a.AdBase
import com.cater.bi.a.AdContainer
import com.cater.bi.a.AdErrorC
import com.cater.bi.a.AdLoadCallback
import com.cater.bi.a.AdShowCallback
import com.cater.bi.a.Adm
import com.cater.bi.a.INativeRender
import com.cater.bi.a.ViewContainer
import com.nice.library_ad_sdk.render.AppNativeAdRender
import com.nice.library_ad_sdk.util.LogUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/** Reusable entry point for loading and displaying configured advertising placements. */
object AdSdkManager {

    private const val TAG = "AdSdkManager"
    private const val LOAD_TIMEOUT_MS = 8_000L
    private const val EMBEDDED_SHOW_TIMEOUT_MS = 6_000L

    /**
     * Initializes the SDK with a bundled JSON fallback and optional Firebase Remote Config key.
     * A consuming product may pass its own raw JSON resource instead of the library default.
     */
    fun initialize(
        context: Context,
        @RawRes bundledConfigResId: Int = R.raw.spaces,
        remoteConfigKey: String? = AdRemoteConfig.KEY_AD_CONFIGURATION,
        debug: Boolean = BuildConfig.DEBUG
    ) {
        AdServer.initialize(context, bundledConfigResId, remoteConfigKey, debug)
    }

    /** Preloads an ad. A failure never throws into page business code. */
    suspend fun preload(
        context: Context,
        placement: String,
        timeoutMillis: Long = LOAD_TIMEOUT_MS
    ): Boolean = withTimeoutOrNull(timeoutMillis) {
        val configured = AdServer.awaitConfiguration(timeoutMillis)
        if (!configured) {
            LogUtils.w(tag = TAG, msg = "Ad configuration is unavailable for placement $placement")
            return@withTimeoutOrNull false
        }
        if (Adm.isAdReady(placement)) {
            return@withTimeoutOrNull true
        }

        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            continuation.invokeOnCancellation { completed.set(true) }
            Adm.preload(context, placement, object : AdLoadCallback {
                override fun onSuccess(placementName: String, ad: AdBase) {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resumeWith(Result.success(true))
                    }
                }

                override fun onFailure(placementName: String, error: AdErrorC) {
                    LogUtils.w(tag = TAG, msg = "Ad load failed for $placementName: ${error.message}")
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resumeWith(Result.success(false))
                    }
                }
            })
        }
    } ?: false

    /** Starts a best-effort preload without blocking the caller. */
    fun preloadAsync(context: Context, placement: String) {
        AdServer.scope.launch {
            preload(context, placement)
        }
    }

    /** Releases a cached or displayed ad and its video/native resources. */
    fun destroy(placement: String) {
        Adm.destroyAd(placement)
    }

    /**
     * Loads (when needed), shows, and waits until the full-screen ad is closed.
     * Returns false on any configuration/load/show failure so callers can
     * continue their normal navigation without being blocked by advertising.
     */
    suspend fun loadAndShow(
        activity: FragmentActivity,
        placement: String,
        loadTimeoutMillis: Long = LOAD_TIMEOUT_MS
    ): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }
        if (!preload(activity, placement, loadTimeoutMillis)) {
            return false
        }
        return showLoaded(activity, placement)
    }

    /** Shows an already preloaded ad and waits until it is closed. */
    suspend fun showLoaded(activity: FragmentActivity, placement: String): Boolean {
        if (activity.isFinishing || activity.isDestroyed || !Adm.isAdReady(placement)) {
            return false
        }

        return suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            continuation.invokeOnCancellation { completed.set(true) }

            fun complete(value: Boolean) {
                if (completed.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resumeWith(Result.success(value))
                }
            }

            Adm.showAd(
                activity,
                placement,
                null,
                null,
                activity,
                object : AdShowCallback {
                    override fun onSuccess(placementName: String, ad: AdBase) = Unit

                    override fun onFailure(placementName: String, error: AdErrorC) {
                        LogUtils.w(tag = TAG, msg = "Ad show failed for $placementName: ${error.message}")
                        complete(false)
                    }

                    override fun onClicked(placementName: String, ad: AdBase) = Unit

                    override fun onClosed(placementName: String, ad: AdBase) {
                        complete(true)
                    }

                    override fun onRewarded(placementName: String, ad: AdBase) = Unit
                }
            )
        }
    }

    /** Loads and attaches an adaptive banner to [container]. */
    suspend fun loadAndShowBanner(
        activity: FragmentActivity,
        placement: String,
        container: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        loadTimeoutMillis: Long = LOAD_TIMEOUT_MS
    ): Boolean = loadAndShowEmbedded(
        activity = activity,
        placement = placement,
        container = ViewContainer(container),
        nativeRender = null,
        lifecycleOwner = lifecycleOwner,
        loadTimeoutMillis = loadTimeoutMillis
    )

    /** Loads and renders a native feed ad in [container]. */
    suspend fun loadAndShowNative(
        activity: FragmentActivity,
        placement: String,
        container: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        loadTimeoutMillis: Long = LOAD_TIMEOUT_MS
    ): Boolean = loadAndShowNative(
        activity = activity,
        placement = placement,
        container = container,
        lifecycleOwner = lifecycleOwner,
        nativeRender = AppNativeAdRender(),
        loadTimeoutMillis = loadTimeoutMillis
    )

    /** Loads and renders a native feed ad with the caller-provided template. */
    suspend fun loadAndShowNative(
        activity: FragmentActivity,
        placement: String,
        container: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        nativeRender: INativeRender,
        loadTimeoutMillis: Long = LOAD_TIMEOUT_MS
    ): Boolean = loadAndShowEmbedded(
        activity = activity,
        placement = placement,
        container = ViewContainer(container),
        nativeRender = nativeRender,
        lifecycleOwner = lifecycleOwner,
        loadTimeoutMillis = loadTimeoutMillis
    )

    private suspend fun loadAndShowEmbedded(
        activity: FragmentActivity,
        placement: String,
        container: AdContainer,
        nativeRender: INativeRender?,
        lifecycleOwner: LifecycleOwner,
        loadTimeoutMillis: Long
    ): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }
        if (!preload(activity, placement, loadTimeoutMillis)) {
            return false
        }

        return withTimeoutOrNull(EMBEDDED_SHOW_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                continuation.invokeOnCancellation { completed.set(true) }

                fun complete(value: Boolean) {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resumeWith(Result.success(value))
                    }
                }

                Adm.showAd(
                    activity,
                    placement,
                    container,
                    nativeRender,
                    lifecycleOwner,
                    object : AdShowCallback {
                        override fun onSuccess(placementName: String, ad: AdBase) {
                            complete(true)
                        }

                        override fun onFailure(placementName: String, error: AdErrorC) {
                            LogUtils.w(tag = TAG, msg = "Embedded ad show failed for $placementName: ${error.message}")
                            complete(false)
                        }

                        override fun onClicked(placementName: String, ad: AdBase) = Unit

                        override fun onClosed(placementName: String, ad: AdBase) {
                            complete(false)
                        }

                        override fun onRewarded(placementName: String, ad: AdBase) = Unit
                    }
                )
            }
        } ?: false
    }
}
