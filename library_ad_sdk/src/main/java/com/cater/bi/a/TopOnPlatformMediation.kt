package com.cater.bi.a


import android.content.Context
import com.nice.library_ad_sdk.util.LogUtils
import com.thinkup.core.api.TUGDPRConsentDismissListener
import com.thinkup.core.api.TUSDK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * TopOn platform mediation implementation
 *
 * created on 2025/8/10
 * @author holmes
 */
object TopOnPlatformMediation : AdPlatformMediation() {

    private val umpState = MutableStateFlow(UmpState.IDLE)
    private var enabledUmp = true

    @Volatile
    private var umpErrorMessage: String? = null
    @Volatile
    private var latestExtras: Map<String, String> = emptyMap()

    private var initedAppId = ""
    private var initedAppKey = ""

    override fun getPlatformType(): String {
        return AdPlatforms.TOPON
    }

    override fun init(
        context: Context,
        appId: String,
        appKey: String,
        extras: Map<String, String>
    ) {
        if (!initCall.compareAndSet(false, true)) {
            return
        }

        try {
            initedAppId = appId
            initedAppKey = appKey
            // Initialize TopOn SDK based on knight project pattern
            val debug = extras["debug"]?.toBoolean() ?: Adm.debug
            if (debug) {
                com.thinkup.core.api.TUSDK.setNetworkLogDebug(true)
                com.thinkup.core.api.TUSDK.integrationChecking(context)
            }

            com.thinkup.core.api.TUSDK.setGDPRUploadDataLevel(
                context,
                com.thinkup.core.api.TUSDK.PERSONALIZED
            )
            // com.thinkup.core.api.TUSDK.initCustomMap(extras)
            updateExtras(extras)
            if (!enabledUmp) {
                com.thinkup.core.api.TUSDK.init(context, appId, appKey)
                onInitSuccess()
            } else {
                onInitSuccess() // mark inited, then ensure by beforeLoadAd
                tryStartConsentFlow(context)
            }
        } catch (e: Exception) {
            onInitFailed("TopOn initialization failed: ${e.message}")
        }
    }

    override fun updateExtras(extras: Map<String, String>) {
        super.updateExtras(extras)
        val channel = extras["channel"]
        LogUtils.d(tag = "TopOn", msg = "update channel:[$channel] extras:$extras")
        com.thinkup.core.api.TUSDK.initCustomMap(extras)
    }

    private fun tryStartConsentFlow(context: Context) {
        val processed = umpState.value.let {
            it == UmpState.READY || it == UmpState.BLOCKED
        }

        if (processed) {
            return
        }

        val activity = ContextActivityContainer(context).getActivity() ?: return
        if (umpState.value == UmpState.LOADING) {
            return
        }

        umpState.value = UmpState.LOADING
        TUSDK.showGDPRConsentDialog(activity, object : TUGDPRConsentDismissListener {
            override fun onDismiss(p0: TUGDPRConsentDismissListener.ConsentDismissInfo?) {
                umpState.value = UmpState.READY
                com.thinkup.core.api.TUSDK.init(context, initedAppId, initedAppKey)
                onInitSuccess()
            }
        })
    }

    override suspend fun beforeLoadAd(context: Context): AdErrorC? {
        if (!enabledUmp) {
            return null
        }

        val processed = umpState.value.let {
            it == UmpState.READY || it == UmpState.BLOCKED
        }

        if (processed) {
            return null
        }

        val activity = ContextActivityContainer(context).getActivity()
        if (activity == null && umpState.value == UmpState.IDLE) {
            return AdErrorC(
                AdErrorC.ERROR_INTERNAL,
                "UMP requires an Activity context before requesting ads"
            )
        }

        tryStartConsentFlow(context)

        val state = withTimeoutOrNull(60_000L) {
            umpState
                .filter { it == UmpState.READY || it == UmpState.BLOCKED }
                .first()
        } ?: return AdErrorC(
            AdErrorC.ERROR_TIMEOUT,
            "UMP consent timeout"
        )

        return when (state) {
            UmpState.READY -> null
            UmpState.BLOCKED -> null
            else -> AdErrorC(
                AdErrorC.ERROR_INTERNAL,
                "UMP consent not granted"
            )
        }
    }

    override fun createBanner(metadata: AdMetadata): AdBase? {
        return TopOnBannerAd(metadata)
    }

    override fun createInterstitial(metadata: AdMetadata): AdBase? {
        return TopOnInterstitialAd(metadata)
    }

    override fun createNative(metadata: AdMetadata): AdBase? {
        return TopOnNativeAd(metadata)
    }

    override fun createRewarded(metadata: AdMetadata): AdBase? {
        return TopOnRewardedAd(metadata)
    }

    override fun createSplash(metadata: AdMetadata): AdBase? {
        return TopOnSplashAd(metadata)
    }

    override fun dumpRevenue(ad: AdBase): Any? {
        return ad.getRevenue()
    }

    override fun getPlatformConfig(): Map<String, Any> {
        return mapOf(
            "platform" to "TopOn",
            "version" to "6.0.0", // Example version
            "initialized" to isInitedSuccess()
        )
    }
}
