package com.cater.bi.a


import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.nice.library_ad_sdk.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread

/**
 * AdMob platform mediation implementation
 * 
 * created on 2025/8/10
 * @author holmes
 */
object AdMobPlatformMediation : AdPlatformMediation() {

    private const val APPLICATION_ID_META = "com.google.android.gms.ads.APPLICATION_ID"

    private val umpState = MutableStateFlow(UmpState.IDLE)
    @Volatile
    private var umpErrorMessage: String? = null
    @Volatile
    private var latestExtras: Map<String, String> = emptyMap()
    @Volatile
    private var consentFlowStarted = false
    
    override fun getPlatformType(): String {
        return AdPlatforms.ADMOB
    }
    
    override fun init(context: Context, appId: String, appKey: String, extras: Map<String, String>) {
        if (!initCall.compareAndSet(false, true)) {
            return
        }

        latestExtras = extras.toMap()
        
        val appContext = context.applicationContext
        val effectiveAppId = appId.takeIf { it.isNotBlank() } ?: readAppIdFromManifest(appContext)

        thread(name = "gma-next-gen-init") {
            try {
                val config = InitializationConfig.Builder(effectiveAppId).build()
                MobileAds.initialize(appContext, config) { initializationStatus ->
                    val statusMap = initializationStatus.adapterStatusMap
                    for (adapterClass in statusMap.keys) {
                        val status = statusMap[adapterClass]
                        LogUtils.d(tag = "AdMob", msg = "Adapter: $adapterClass, Status: ${status?.description}, Latency: ${status?.latency}")
                    }
                }
                Adm.handler.post { onInitSuccess() }
            } catch (e: Exception) {
                Adm.handler.post { onInitFailed("AdMob initialization failed: ${e.message}") }
            }
        }

        tryStartConsentFlow(context)
    }

    private fun readAppIdFromManifest(context: Context): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString(APPLICATION_ID_META).orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    override fun updateExtras(extras: Map<String, String>) {
        latestExtras = extras.toMap()
    }

    override suspend fun beforeLoadAd(context: Context): AdErrorC? {
        val consentManager = GoogleMobileAdsConsentManager.getInstance(context.applicationContext)
        tryStartConsentFlow(context)
        if (consentManager.canRequestAds) {
            return null
        }

        val activity = ContextActivityContainer(context).getActivity()
        if (activity == null && umpState.value == UmpState.IDLE) {
            return AdErrorC(
                AdErrorC.ERROR_INTERNAL,
                "AdMob UMP requires an Activity context before requesting ads"
            )
        }

        tryStartConsentFlow(context)
        if (consentManager.canRequestAds) {
            umpState.value = UmpState.READY
            return null
        }

        val state = withTimeoutOrNull(60_000L) {
            umpState
                .filter { it == UmpState.READY || it == UmpState.BLOCKED }
                .first()
        } ?: return AdErrorC(
            AdErrorC.ERROR_TIMEOUT,
            "AdMob UMP consent timeout"
        )

        return when (state) {
            UmpState.READY -> null
            UmpState.BLOCKED -> AdErrorC(
                AdErrorC.ERROR_INTERNAL,
                umpErrorMessage ?: "AdMob UMP consent does not permit ad requests"
            )
            else -> AdErrorC(
                AdErrorC.ERROR_INTERNAL,
                umpErrorMessage ?: "AdMob UMP consent not granted"
            )
        }
    }

    private fun tryStartConsentFlow(context: Context) {
        val consentManager = GoogleMobileAdsConsentManager.getInstance(context.applicationContext)
        val activity = ContextActivityContainer(context).getActivity() ?: return
        if (consentFlowStarted) {
            return
        }

        consentFlowStarted = true
        umpState.value = UmpState.LOADING
        consentManager.testDeviceId = latestExtras["admob_ump_test_device_id"]
        consentManager.gatherConsent(activity) { error ->
            umpErrorMessage = error?.message
            umpState.value = if (consentManager.canRequestAds) {
                UmpState.READY
            } else {
                UmpState.BLOCKED
            }
        }
    }

    fun isPrivacyOptionsRequired(context: Context): Boolean {
        val consentManager = GoogleMobileAdsConsentManager.getInstance(context.applicationContext)
        return consentManager.isPrivacyOptionsRequired
    }

    fun showPrivacyOptionsForm(activity: Activity, onDismiss: (AdErrorC?) -> Unit) {
        val consentManager = GoogleMobileAdsConsentManager.getInstance(activity.applicationContext)
        consentManager.showPrivacyOptionsForm(activity) { formError ->
            if (formError == null) {
                onDismiss(null)
            } else {
                onDismiss(
                    AdErrorC(
                        AdErrorC.ERROR_INTERNAL,
                        formError.message
                    )
                )
            }
        }
    }
    
    override fun createBanner(metadata: AdMetadata): AdBase? {
        return AdMobBannerAd(metadata)
    }
    
    override fun createInterstitial(metadata: AdMetadata): AdBase? {
        return AdMobInterstitialAd(metadata)
    }
    
    override fun createNative(metadata: AdMetadata): AdBase? {
        return AdMobNativeAd(metadata)
    }
    
    override fun createRewarded(metadata: AdMetadata): AdBase? {
        return AdMobRewardedAd(metadata)
    }
    
    override fun createSplash(metadata: AdMetadata): AdBase? {
        return AdMobSplashAd(metadata)
    }
    
    override fun dumpRevenue(ad: AdBase): Any? {
        return ad.getRevenue()
    }
    
    override fun getPlatformConfig(): Map<String, Any> {
        return mapOf(
            "platform" to "AdMob",
            "version" to "1.4.0",
            "initialized" to isInitedSuccess()
        )
    }
}
