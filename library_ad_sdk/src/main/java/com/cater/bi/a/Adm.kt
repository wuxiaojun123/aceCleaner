package com.cater.bi.a

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.cater.bi.e.Eve
import com.drakeet.purewriter.ObscureDefaultLifecycleObserver
import com.drakeet.purewriter.addObserver
import com.drakeet.purewriter.removeObscureObserver
import com.nice.library_ad_sdk.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Ad manager - Main entry point for ad operations
 *
 * created on 2025/8/10
 * @author holmes
 */
object Adm {
    private const val TAG = "adm"

    private var app: Application? = null
    private var _initialized = false
    private val _debug = MutableStateFlow(false)
    private val appliedConfiguration = MutableStateFlow<AdConfiguration?>(null)

    var debug: Boolean
        get() = _debug.value
        set(value) {
            _debug.value = value
        }

    val scope: CoroutineScope
        get() = AdPlatformManager.scope

    val handler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }

    private val autoInitPlatform = MutableStateFlow(true)
    private var initPlatformCalled = false
    private var extraBundle = emptyMap<String, String>()
    private val preloadCallbacks = ConcurrentHashMap<String, MutableList<AdLoadCallback>>()
    private val preloadCallbacksLock = Any()

    /**
     * Initialize ad manager
     */
    fun init(application: Application, autoInitp: Boolean) {
        this.app = application
        this._initialized = true
        this.autoInitPlatform.value = autoInitp
    }

    fun setAutoInitPlatform(autoInitp: Boolean) {
        this.autoInitPlatform.value = autoInitp
    }

    fun isInitialized(): Boolean {
        return _initialized
    }

    /**
     * if config is applied, return not null
     */
    fun getCfgStateFlow(): Flow<AdConfiguration?> {
        return appliedConfiguration
    }

    /**
     * Set ad configuration from JSON structure
     * Optimization: Register placements immediately on config change, not after platform init
     */
    fun setConfiguration(
        configuration: AdConfiguration,
        callback: AdRegistrationCallback? = null
    ) {
        try {
            // This will clear old config/placements and register new ones immediately
            AdConfigurationManager.setConfiguration(configuration)

            // Initialize platform asynchronously (don't wait for it to register placements)
            run {
                if (app != null) {
                    if (!autoInitPlatform.value && !initPlatformCalled) {
                        // auto init platform disabled
                        callback?.onSuccess("Configuration set2")
                        return@run
                    }

                    initializePlatform(
                        app!!,
                        configuration.platform,
                        object : AdPlatformInitCallback {
                            override fun onSuccess(platformName: String) {
                                callback?.onSuccess(platformName)
                            }

                            override fun onFailure(platformName: String, error: AdErrorC) {
                                callback?.onFailure(platformName, error)
                            }
                        })
                } else {
                    callback?.onSuccess("Configuration set")
                }
            }
            appliedConfiguration.value = configuration
        } catch (e: Exception) {
            val error = AdErrorC(AdErrorC.ERROR_INTERNAL, "Configuration failed: ${e.message}", e)
            callback?.onFailure("", error)
        }
    }

    /**
     * Initialize platform
     */
    fun initializePlatform(
        context: Context,
        platform: AdPlatform,
        callback: AdPlatformInitCallback? = null
    ) {
        if (!initPlatformCalled) {
            initPlatformCalled = true
        }
        AdPlatformManager.initializePlatform(context, platform, callback, extraBundle)
    }

    fun isPlatFlowOk(): Boolean {
        return autoInitPlatform.value || initPlatformCalled
    }

    fun tryManualInitPlatform(what: String) {
        if (app != null) {
            if (initPlatformCalled) {
                // platform already inited
                return
            }

            val config = appliedConfiguration.value
            if (config == null) {
                // no config
                LogUtils.w(tag = TAG, msg = "manual intp failed, no config")
                // next config should auto initialize
                if (!initPlatformCalled) {
                    initPlatformCalled = true
                }
                return
            }

            Eve.send("ad_initp", mapOf(
                "what" to what,
                "platform" to config.platform.name
            ))

            initializePlatform(app!!, config.platform, object : AdPlatformInitCallback {
                override fun onSuccess(platformName: String) {
                    LogUtils.d(tag = TAG, msg = "init ok")
                }

                override fun onFailure(platformName: String, error: AdErrorC) {
                    LogUtils.w(tag = TAG, msg = "init fail")
                }
            })
        }
    }

    /**
     * Register single placement with platform
     */
    fun registerPlacement(
        placement: AdPlacement,
        platform: AdPlatform,
        callback: AdRegistrationCallback? = null
    ) {
        try {
            AdMaterialManager.registerMaterial(placement, platform)
            callback?.onSuccess(placement.id)

            // Auto load if configured
            val autoLoad = placement.getExtra<Boolean>("autoLoad", false)
            if (autoLoad && app != null) {
                loadAd(app!!, placement.id, null)
            }
        } catch (e: Exception) {
            val error = AdErrorC(AdErrorC.ERROR_INTERNAL, "Registration failed: ${e.message}", e)
            callback?.onFailure(placement.id, error)
        }
    }

    /**
     * fast path to bypass platform wait
     */
    private fun shouldWaitPlat(): Boolean {
        return isPlatFlowOk()
    }

    private enum class PreloadAction {
        RETURN_READY,
        JOIN_LOADING,
        SKIP_EXTERNAL_LOADING,
        START_LOADING,
    }

    private fun registerPreload(position: String, callback: AdLoadCallback?): PreloadAction {
        synchronized(preloadCallbacksLock) {
            if (peekReadyAd(position) != null) {
                return PreloadAction.RETURN_READY
            }

            val callbacks = preloadCallbacks[position]
            if (callbacks != null) {
                if (callback != null) {
                    callbacks.add(callback)
                }
                return PreloadAction.JOIN_LOADING
            }

            if (isLoading(position)) {
                return PreloadAction.SKIP_EXTERNAL_LOADING
            }

            preloadCallbacks[position] = mutableListOf<AdLoadCallback>().apply {
                if (callback != null) {
                    add(callback)
                }
            }
            return PreloadAction.START_LOADING
        }
    }

    private fun takePreloadCallbacks(position: String): List<AdLoadCallback> {
        synchronized(preloadCallbacksLock) {
            return preloadCallbacks.remove(position)?.toList().orEmpty()
        }
    }

    private fun notifyPreloadSuccess(position: String, ad: AdBase) {
        takePreloadCallbacks(position).forEach { callback ->
            runCatching {
                callback.onSuccess(position, ad)
            }.onFailure {
                LogUtils.w(tag = TAG, msg = "preload success callback failed: ${it.message}")
            }
        }
    }

    private fun notifyPreloadFailure(position: String, error: AdErrorC) {
        takePreloadCallbacks(position).forEach { callback ->
            runCatching {
                callback.onFailure(position, error)
            }.onFailure {
                LogUtils.w(tag = TAG, msg = "preload failure callback failed: ${it.message}")
            }
        }
    }

    /**
     * Preload ad for placement.
     * Optimization: If ad is already ready, return immediately. If ad is loading, join the loading callbacks.
     * Only start loading if not already loading.
     */
    fun preload(context: Context, position: String, callback: AdLoadCallback?) {
        val ready = peekReadyAd(position)
        if (ready != null) {
            callback?.onSuccess(position, ready)
            return
        }

        when (registerPreload(position, callback)) {
            PreloadAction.RETURN_READY -> {
                val sharedReady = peekReadyAd(position)
                if (sharedReady != null) {
                    callback?.onSuccess(position, sharedReady)
                    return
                }
            }

            PreloadAction.JOIN_LOADING -> {
                LogUtils.d(tag = "abi", msg = "join loading [${position}]")
                return
            }

            PreloadAction.SKIP_EXTERNAL_LOADING -> {
                LogUtils.d(tag = "abi", msg = "is loading [${position}]")
                return
            }

            PreloadAction.START_LOADING -> Unit
        }

        loadAd(context, position, object : AdLoadCallback {
            override fun onSuccess(placementName: String, ad: AdBase) {
                notifyPreloadSuccess(position, ad)
            }

            override fun onFailure(placementName: String, error: AdErrorC) {
                notifyPreloadFailure(position, error)
            }
        })
    }

    /**
     * Load ad for placement
     * Optimization: Wait for platform initialization before loading (similar to TPAStrategy1)
     */
    fun loadAd(
        context: Context,
        placementName: String,
        callback: AdLoadCallback?
    ) {
        val logCallback = LogLoadCallback(AnalyseLoadCallback(callback))
        val metadata = AdMaterialManager.getMetadata(placementName)
        if (metadata == null) {
            val error = AdErrorC(
                AdErrorC.ERROR_INVALID_REQUEST,
                "Placement '$placementName' not registered"
            )
            logCallback?.onFailure(placementName, error)
            return
        }

        // Use coroutine scope similar to TPAStrategy1 pattern
        AdPlatformManager.scope.launch {
            if (!shouldWaitPlat()) {
                // platform not inited
                val error = AdErrorC(
                    AdErrorC.ERROR_INTERNAL,
                    "Platform '${metadata.platform.name}' initialization timeout"
                )
                logCallback?.onFailure(placementName, error)
                return@launch
            }

            try {
                // Wait for platform initialization (similar to AdHelper.waitSdkOk)
                val platformReady = AdPlatformWaiter.waitPlatformReady(
                    metadata.platform.name,
                    10_000L // 10 second timeout like TPAStrategy1
                )

                if (!platformReady) {
                    val error = AdErrorC(
                        AdErrorC.ERROR_TIMEOUT,
                        "Platform '${metadata.platform.name}' initialization timeout"
                    )
                    logCallback?.onFailure(placementName, error)
                    return@launch
                }

                val beforeLoadError =
                    AdPlatformManager.beforeLoadAd(context, metadata.platform.name)
                if (beforeLoadError != null) {
                    logCallback?.onFailure(placementName, beforeLoadError)
                    return@launch
                }

                // Platform is ready, proceed with ad loading
                val ad = AdFactory.createAd(metadata)
                AdMaterialManager.setAdInstance(placementName, ad)
                AdMaterialManager.setState(placementName, AdState.LOADING)

                // This is emitted only when the request is about to be passed
                // to the ad SDK, making it the denominator for load results.
                Eve.send(
                    "ad_load_request", mapOf(
                        "scenario_code" to metadata.placement.id,
                        "unit_code" to ad.getUnitCode(),
                        "ad_format" to ad.getAdType(),
                        "ad_platform" to ad.getPlatform()
                    )
                )

                ad.load(context, object : AdLoadListener {
                    override fun onAdLoaded(ad: AdBase) {
                        AdMaterialManager.setState(placementName, AdState.LOADED)
                        logCallback?.onSuccess(placementName, ad)
                    }

                    override fun onAdFailedToLoad(ad: AdBase, error: AdErrorC) {
                        error.adObj = ad
                        if (error.code != AdErrorC.ERROR_TIMEOUT) {
                            AdMaterialManager.removeAdInstance(placementName)
                        }
                        AdMaterialManager.setState(placementName, AdState.FAILED)
                        logCallback?.onFailure(placementName, error)
                    }
                })
            } catch (e: Exception) {
                val error = AdErrorC(AdErrorC.ERROR_INTERNAL, "Load failed: ${e.message}", e)
                AdMaterialManager.setState(placementName, AdState.FAILED)
                logCallback?.onFailure(placementName, error)
            }
        }
    }

    /**
     * Show ad for placement
     */
    fun showAd(
        context: Context,
        placementName: String,
        container: AdContainer?,
        nativeRender: INativeRender?,
        lifecycleOwner: LifecycleOwner?,
        callback: AdShowCallback?
    ) {
        val logCallback = LogShowCallback(AnalyseShowCallback(callback))
        val metadata = AdMaterialManager.getMetadata(placementName)

        // A show request represents the product trying to display an ad. It
        // deliberately precedes the ready check, so failures such as “not
        // loaded” and “not ready” can be counted in the same funnel.
        Eve.send(
            "ad_show_request", mapOf(
                "scenario_code" to (metadata?.placement?.id ?: placementName),
                "unit_code" to (metadata?.getPrimaryUnitId() ?: ""),
                "ad_format" to (metadata?.placement?.type ?: ""),
                "ad_platform" to (metadata?.platform?.name ?: "")
            )
        )
        val ad = AdMaterialManager.getAdInstance(placementName)
        if (ad == null) {
            val error =
                AdErrorC(AdErrorC.ERROR_NOT_READY, "Ad for placement '$placementName' not loaded")
            logCallback?.onFailure(placementName, error)
            return
        }

        if (!AdMaterialManager.isReady(placementName)) {
            val error =
                AdErrorC(AdErrorC.ERROR_NOT_READY, "Ad for placement '$placementName' not ready")
            logCallback?.onFailure(placementName, error)
            return
        }

        val doShow = Runnable {
            try {
                AdMaterialManager.setState(placementName, AdState.SHOWING)

                ad.show(context, container, nativeRender, object : AdShowListener {

                    private var disposeLifecycleBind: Runnable? = null

                    override fun onAdShown(ad: AdBase) {
                        AdMaterialManager.setState(placementName, AdState.SHOWN)
                        logCallback?.onSuccess(placementName, ad)

                        if (lifecycleOwner != null) {
                            val observer = object :
                                ObscureDefaultLifecycleObserver {
                                override fun onDestroy(owner: LifecycleOwner) {
                                    super.onDestroy(owner)
                                    lifecycleOwner.lifecycle.removeObscureObserver(this)
                                    runCatching {
                                        ad.destroy()
                                    }.onFailure {
                                        LogUtils.w(tag = "adm", msg = "destroy ad failed: ${it.message}")
                                    }
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            disposeLifecycleBind = object : Runnable {
                                override fun run() {
                                    lifecycleOwner.lifecycle.removeObscureObserver(observer)
                                    disposeLifecycleBind = null
                                }
                            }
                        }
                    }

                    override fun onAdFailedToShow(ad: AdBase, error: AdErrorC) {
                        error.adObj = ad
                        AdMaterialManager.setState(placementName, AdState.FAILED)
                        logCallback?.onFailure(placementName, error)
                    }

                    override fun onAdClicked(ad: AdBase) {
                        logCallback?.onClicked(placementName, ad)
                    }

                    override fun onAdClosed(ad: AdBase) {
                        AdMaterialManager.removeAdInstance(placementName)
                        logCallback?.onClosed(placementName, ad)

                        // Auto reload if configured
                        val metadata = AdMaterialManager.getMetadata(placementName)
                        val autoLoad = metadata?.getExtra<Boolean>("autoLoad", false) ?: false
                        if (autoLoad) {
                            loadAd(context, placementName, null)
                        }
                        disposeLifecycleBind?.run()
                    }

                    override fun onAdRewarded(ad: AdBase) {
                        logCallback?.onRewarded(placementName, ad)
                    }
                })
            } catch (e: Exception) {
                val error = AdErrorC(AdErrorC.ERROR_INTERNAL, "Show failed: ${e.message}", e)
                AdMaterialManager.setState(placementName, AdState.FAILED)
                logCallback?.onFailure(placementName, error)
            }
        }

        if (lifecycleOwner == null) {
            doShow.run()
        } else {
            val lifecycle = lifecycleOwner.lifecycle
            if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
                logCallback.onFailure(
                    placementName,
                    AdErrorC(AdErrorC.ERROR_LIFECYCLE, "Lifecycle is destroyed")
                )
                return
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                doShow.run()
                return
            }
            lifecycle.addObserver(
                object : ObscureDefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        super.onResume(owner)
                        lifecycle.removeObscureObserver(this)
                        doShow.run()
                    }

                    override fun onDestroy(owner: LifecycleOwner) {
                        super.onDestroy(owner)
                        lifecycle.removeObscureObserver(this)
                    }
                }
            )
        }

    }

    /**
     * Check if ad is ready for placement
     */
    fun isAdReady(placementName: String): Boolean {
        return AdMaterialManager.isReady(placementName)
    }

    fun peekReadyAd(placementName: String): AdBase? {
        if (AdMaterialManager.isReady(placementName)) {
            return AdMaterialManager.getAdInstance(placementName)
        }
        return null
    }

    /**
     * Get ad state for placement
     */
    fun getAdState(placementName: String): String {
        return AdMaterialManager.getState(placementName)
    }

    fun isLoading(placementName: String): Boolean {
        return getAdState(placementName) == AdState.LOADING
    }

    /**
     * Get ad eCPM for placement
     */
    fun getAdEcpm(placementName: String): Double {
        return AdMaterialManager.getAdInstance(placementName)?.getEcpm() ?: 0.0
    }

    /**
     * Get ad statistics for placement
     */
    fun getAdStats(placementName: String): AdStats? {
        return AdMaterialManager.getAdStats(placementName)
    }

    /**
     * Destroy ad for placement
     */
    fun destroyAd(placementName: String) {
        AdMaterialManager.removeAdInstance(placementName)?.destroy()
        AdMaterialManager.setState(placementName, AdState.IDLE)
    }

    /**
     * Destroy all ads
     */
    fun destroyAllAds() {
        AdMaterialManager.clearAll()
    }

    /**
     * Load and show ad in one call
     * @param autoLoad whether to auto load next ad after show
     */
    fun loadAndShow(
        context: Context,
        placementName: String,
        container: AdContainer?,
        nativeRender: INativeRender?,
        lifecycleOwner: LifecycleOwner?,
        loadCallback: AdLoadCallback?,
        showCallback: AdShowCallback?,
        autoLoad: Boolean
    ) {
        val logLoad = loadCallback // LogLoadCallback(loadCallback)
        val logShow = showCallback // LogShowCallback(showCallback)

        val showRun = object : Runnable {
            private var once = false
            override fun run() {
                if (once) {
                    return
                }
                once = true

                showAd(
                    context,
                    placementName,
                    container,
                    nativeRender,
                    lifecycleOwner,
                    object : AdShowCallback {
                        override fun onSuccess(placementName: String, ad: AdBase) {
                            logShow?.onSuccess(placementName, ad)
                            if (autoLoad && ad.getAdType() == AdType.NATIVE) {
                                preload(context, placementName, null)
                            }
                        }

                        override fun onFailure(placementName: String, error: AdErrorC) {
                            logShow?.onFailure(placementName, error)
                        }

                        override fun onClicked(placementName: String, ad: AdBase) {
                            logShow?.onClicked(placementName, ad)
                        }

                        override fun onClosed(placementName: String, ad: AdBase) {
                            logShow?.onClosed(placementName, ad)
                            if (autoLoad) {
                                preload(context, placementName, null)
                            }
                        }

                        override fun onRewarded(placementName: String, ad: AdBase) {
                            logShow?.onRewarded(placementName, ad)
                        }
                    })
            }
        }

        if (isAdReady(placementName)) {
            showRun.run()
            return
        }

        preload(context, placementName, object : AdLoadCallback {
            override fun onSuccess(placementName: String, ad: AdBase) {
                logLoad?.onSuccess(placementName, ad)
                showRun.run()
            }

            override fun onFailure(placementName: String, error: AdErrorC) {
                logLoad?.onFailure(placementName, error)
            }
        })
    }

    /**
     * Get all registered placements
     */
    fun getRegisteredPlacements(): Set<String> {
        return AdMaterialManager.getRegisteredPlacements()
    }

    /**
     * Check if platform is initialized
     */
    fun isPlatformInitialized(platformName: String): Boolean {
        return AdPlatformManager.isPlatformInitialized(platformName)
    }

    /**
     * Get platform configuration
     */
    fun getPlatformConfig(platformName: String): Map<String, Any>? {
        return AdPlatformManager.getPlatformConfig(platformName)
    }

    /**
     * Update platform extras
     */
    fun updatePlatformExtras(platformName: String, extras: Map<String, String>) {
        AdPlatformManager.updatePlatformExtras(platformName, extras)
    }

    fun updatePlatformExtras(extras: Map<String, String>) {
        extraBundle = extras.toMap()
        val plat = appliedConfiguration.value?.platform?.name
        if (!plat.isNullOrEmpty()) {
            updatePlatformExtras(plat, extraBundle)
        }
    }

    /**
     * Get supported platforms
     */
    fun getSupportedPlatforms(): Set<String> {
        return AdPlatformManager.getRegisteredPlatforms()
    }
}
