package com.cater.bi.a

import android.content.Context
import android.view.ViewGroup
import com.thinkup.banner.api.TUBannerListener
import com.thinkup.banner.api.TUBannerView
import com.thinkup.core.api.TUAdInfo
import com.thinkup.core.api.AdError
import com.thinkup.interstitial.api.TUInterstitial
import com.thinkup.interstitial.api.TUInterstitialListener
import com.thinkup.rewardvideo.api.TURewardVideoAd
import com.thinkup.rewardvideo.api.TURewardVideoListener
import com.thinkup.splashad.api.TUSplashAd
import com.thinkup.splashad.api.TUSplashAdListener
import com.thinkup.splashad.api.TUSplashAdExtraInfo
import android.widget.TextView
import com.thinkup.nativead.api.NativeAd
import com.thinkup.nativead.api.TUNative
import com.thinkup.nativead.api.TUNativeAdView
import com.thinkup.nativead.api.TUNativeEventListener
import com.thinkup.nativead.api.TUNativeImageView
import com.thinkup.nativead.api.TUNativeMaterial
import com.thinkup.nativead.api.TUNativeNetworkListener
import com.thinkup.nativead.api.TUNativePrepareInfo
import android.widget.FrameLayout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TopOn ad implementations
 *
 * created on 2025/8/10
 * @author holmes
 */

abstract class TopOnAds(metadata: AdMetadata) : AbstractAd(metadata) {

    private var showSourceNetwork = ""

    protected fun putRevenue(adInfo: TUAdInfo) {
        if (materialRevenue != null) {
            return
        }
        showSourceNetwork = adInfo.networkFirmId.toString()
        materialRevenue = AdRevenue(
            getPlatform(),
            adInfo.networkFirmId.toString(),
            getAdType(),
            adInfo.adsourceId,
            adInfo.publisherRevenue,
            adInfo.currency
        )
    }

    override fun getSourceNetwork(): String {
        return showSourceNetwork
    }

}

class TopOnBannerAd(metadata: AdMetadata) : TopOnAds(metadata) {

    private var bannerView: TUBannerView? = null

    override fun getPlatform(): String = "TopOn"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val bannerAdView = TUBannerView(context)
        bannerAdView.setPlacementId(unitId)

        bannerAdView.setBannerAdListener(object : TUBannerListener {
            override fun onBannerLoaded() {
                this@TopOnBannerAd.onLoadSuccess()
            }

            override fun onBannerFailed(error: com.thinkup.core.api.AdError?) {
                this@TopOnBannerAd.onLoadFailed(
                    AdErrorC(
                        error?.code ?: "unknown",
                        error?.desc ?: "Unknown error"
                    )
                )
            }

            override fun onBannerClicked(adInfo: TUAdInfo?) {
                this@TopOnBannerAd.onAdClicked()
            }

            override fun onBannerShow(adInfo: TUAdInfo?) {
                this@TopOnBannerAd.onShowSuccess()
                adInfo?.let { info ->
                    materialEcpm = info.ecpm
                    putRevenue(adInfo)
                }
            }

            override fun onBannerClose(adInfo: TUAdInfo?) {
                this@TopOnBannerAd.onAdClosed()
            }

            override fun onBannerAutoRefreshed(adInfo: TUAdInfo?) {
                // Handle auto refresh if needed
            }

            override fun onBannerAutoRefreshFail(error: com.thinkup.core.api.AdError?) {
                // Handle auto refresh failure if needed
            }
        })

        bannerAdView.loadAd()
        bannerView = bannerAdView
    }

    override fun showAd(context: Context, container: AdContainer?) {
        val ad = bannerView
        if (ad == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Ad not loaded"))
            return
        }

        if (container == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Container is null"))
            return
        }

        (ad.parent as? ViewGroup)?.removeView(ad)
        container.getParent()?.removeAllViews()
        container.getParent()?.addView(
            ad, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    override fun destroyAd() {
        bannerView?.destroy()
        bannerView = null
    }
}

class TopOnInterstitialAd(metadata: AdMetadata) : TopOnAds(metadata) {

    private var interstitialAd: TUInterstitial? = null

    override fun getPlatform(): String = "TopOn"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val interstitial = TUInterstitial(context, unitId)

        interstitial.setAdListener(object : TUInterstitialListener {
            override fun onInterstitialAdLoaded() {
                this@TopOnInterstitialAd.onLoadSuccess()
            }

            override fun onInterstitialAdLoadFail(error: com.thinkup.core.api.AdError) {
                this@TopOnInterstitialAd.onLoadFailed(AdErrorC(error.code.toString(), error.desc))
            }

            override fun onInterstitialAdClicked(adInfo: TUAdInfo) {
                val revenue = adInfo.publisherRevenue
                materialEcpm = adInfo.ecpm
                putRevenue(adInfo)
                this@TopOnInterstitialAd.onAdClicked()
            }

            override fun onInterstitialAdShow(adInfo: TUAdInfo) {
                val revenue = adInfo.publisherRevenue
                materialEcpm = adInfo.ecpm
                putRevenue(adInfo)
                this@TopOnInterstitialAd.onShowSuccess()
            }

            override fun onInterstitialAdClose(adInfo: TUAdInfo) {
                this@TopOnInterstitialAd.onAdClosed()
            }

            override fun onInterstitialAdVideoStart(adInfo: TUAdInfo) {
                // Video start callback
            }

            override fun onInterstitialAdVideoEnd(adInfo: TUAdInfo) {
                // Video end callback
            }

            override fun onInterstitialAdVideoError(error: com.thinkup.core.api.AdError) {
                // Video error callback
            }
        })

        interstitial.load()
        interstitialAd = interstitial
    }

    override fun showAd(context: Context, container: AdContainer?) {
        val ad = interstitialAd
        if (ad == null || !ad.isAdReady) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Ad not ready"))
            return
        }

        val activity = context as? android.app.Activity
        if (activity == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Context is not an Activity"))
            return
        }

        ad.show(activity)
    }

    override fun destroyAd() {
        // interstitialAd?.onDestroy()
        interstitialAd = null
    }
}

class TopOnNativeAd(metadata: AdMetadata) : TopOnAds(metadata) {

    private var tmpAd: TUNative? = null
    private var attachedMaterial: NativeAd? = null

    private val tpListener = object : TUNativeNetworkListener {
        override fun onNativeAdLoaded() {
            kotlin.runCatching {
                val ad = tmpAd ?: return@runCatching
                val ecpm = ad.checkAdStatus().tuTopAdInfo.ecpm
                this@TopOnNativeAd.materialEcpm = ecpm
            }
            this@TopOnNativeAd.onLoadSuccess()
        }

        override fun onNativeAdLoadFail(error: AdError?) {
            this@TopOnNativeAd.onLoadFailed(
                AdErrorC(error?.code ?: "unknown", error?.desc ?: "load fail")
            )
        }
    }

    override fun getPlatform(): String = "TopOn"

    override fun isReady(): Boolean {
        return tmpAd?.checkAdStatus()?.isReady ?: false
    }

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val ad = TUNative(context, unitId, tpListener)
        tmpAd = ad
        ad.makeAdRequest()
    }

    override fun showAd(context: Context, container: AdContainer?) {
        onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Native ad requires render interface"))
    }

    override fun showAd(context: Context, container: AdContainer?, render: INativeRender?) {
        if (context !is android.app.Activity) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Context must be an Activity"))
            return
        }

        val ad = tmpAd
        if (ad == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Ad not loaded"))
            return
        }

        val sceneCode = getSceneCode()
        if (!sceneCode.isNullOrEmpty()) {
            TUNative.entryAdScenario(getUnitCode(), sceneCode)
        }

        if (!ad.checkAdStatus().isReady) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_NOT_READY, "Ad not ready"))
            return
        }

        if (container == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Container is required"))
            return
        }

        if (render == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Native ad requires render interface"))
            return
        }

        val nativeAd = if (!sceneCode.isNullOrEmpty()) {
            ad.getNativeAd(sceneCode)
        } else {
            ad.nativeAd
        }

        nativeAd.setNativeEventListener(object : TUNativeEventListener {
            override fun onAdImpressed(adView: TUNativeAdView?, adInfo: TUAdInfo?) {
                kotlin.runCatching {
                    val info = adInfo ?: return@runCatching
                    val revenue = info.ecpm
                    materialEcpm = revenue
                    putRevenue(adInfo)
                }
                this@TopOnNativeAd.onShowSuccess()
            }

            override fun onAdClicked(adView: TUNativeAdView?, adInfo: TUAdInfo?) {
                this@TopOnNativeAd.onAdClicked()
            }

            override fun onAdVideoStart(adView: TUNativeAdView?) {}
            override fun onAdVideoEnd(adView: TUNativeAdView?) {}
            override fun onAdVideoProgress(adView: TUNativeAdView?, progress: Int) {}
        })

        val suit = suitContainer(container.getParent()!!)!!
        var tpNativeView: TUNativeAdView? = null
        var prepareInfo: TUNativePrepareInfo? = null

        suit.removeAllViews()
        try {
            if (nativeAd.isNativeExpress) {
                // Template rendering
                val root = TUNativeAdView(context)
                val lp = ViewGroup.MarginLayoutParams(
                    ViewGroup.MarginLayoutParams.MATCH_PARENT,
                    ViewGroup.MarginLayoutParams.WRAP_CONTENT
                )
                suit.addView(root, lp)
                nativeAd.renderAdContainer(root, null)
                tpNativeView = root
            } else {
                // Self rendering
                prepareInfo = TUNativePrepareInfo()
                tpNativeView = bindSelfRenderView(
                    context,
                    nativeAd,
                    nativeAd.adMaterial,
                    suit,
                    prepareInfo,
                    render
                )
            }

            nativeAd.prepare(tpNativeView, prepareInfo)
            attachedMaterial = nativeAd
        } catch (e: Exception) {
            onShowFailed(
                AdErrorC(
                    AdErrorC.ERROR_INTERNAL,
                    "Failed to bind native ad: ${e.message}"
                )
            )
        }
    }

    private fun suitContainer(container: ViewGroup): ViewGroup? {
        return container
    }

    private fun getSceneCode(): String? {
        return metadata.getExtra<String>("sceneCode")
    }

    private fun bindSelfRenderView(
        context: android.app.Activity,
        nativeAd: NativeAd,
        adMaterial: TUNativeMaterial,
        container: ViewGroup,
        prepareInfo: TUNativePrepareInfo,
        render: INativeRender
    ): TUNativeAdView {
        // Get root view from render
        val rootView = render.getRoot(context, container, nativeAd)

        // Populate text content
        render.getTitleView()?.let {
            it.text = adMaterial.title
            prepareInfo.titleView = it
        }

        render.getDescView()?.let {
            it.text = adMaterial.descriptionText
            prepareInfo.descView = it
        }

        render.getActionView()?.let { actionView ->
            if (actionView is TextView) {
                actionView.text = adMaterial.callToActionText
            }
            prepareInfo.ctaView = actionView
        }

        // Handle icon
        val icon = adMaterial.adIconView
        val iconUrl = adMaterial.iconImageUrl

        if (icon != null) {
            val oIconView = render.getIconView()
            oIconView?.let { originalIcon ->
                icon.id = originalIcon.id
                icon.layoutParams = originalIcon.layoutParams
                (originalIcon.parent as? ViewGroup)?.let { parent ->
                    parent.removeView(originalIcon)
                    parent.addView(icon)
                }
            }
            prepareInfo.setIconView(icon)
        } else if (!iconUrl.isNullOrEmpty()) {
            val iconView = TUNativeImageView(context)
            iconView.setImage(iconUrl)

            val oIconView = render.getIconView()
            oIconView?.let { originalIcon ->
                iconView.id = originalIcon.id
                iconView.layoutParams = originalIcon.layoutParams
                (originalIcon.parent as? ViewGroup)?.let { parent ->
                    parent.removeView(originalIcon)
                    parent.addView(iconView)
                }
            }
            prepareInfo.setIconView(iconView)
        }

        // Handle logo
        if (adMaterial.adLogoView != null) {
            val logo = adMaterial.adLogoView
            val oLogoView = render.getLogoView()
            oLogoView?.let { originalLogo ->
                logo.id = originalLogo.id
                logo.layoutParams = originalLogo.layoutParams
                (originalLogo.parent as? ViewGroup)?.let { parent ->
                    parent.removeView(originalLogo)
                    parent.addView(logo)
                }
            }
            prepareInfo.adLogoView = logo
        } else {
            val adFrom = adMaterial.adFrom
            render.getLogoView()?.let { logoView ->
                if (!adFrom.isNullOrEmpty()) {
                    if (logoView is TextView) {
                        logoView.text = adFrom
                        prepareInfo.adFromView = logoView
                    }
                }
            }
        }

        // Yandex specific: Handle domain text
        val domain = adMaterial.domain
        if (!domain.isNullOrEmpty()) {
            render.getDomainView()?.let { domainView ->
                domainView.text = domain
                prepareInfo.setDomainView(domainView)
            }
        }

        // Yandex specific: Handle warning text
        val warning = adMaterial.warning
        if (!warning.isNullOrEmpty()) {
            render.getWarningView()?.let { warningView ->
                warningView.text = warning
                prepareInfo.setWarningView(warningView)
            }
        }

        // Yandex specific: Handle adFrom text (if not already handled by logo)
        val adFrom2 = adMaterial.adFrom
        if (!adFrom2.isNullOrEmpty() && prepareInfo.adFromView == null) {
            render.getAdFromView()?.let { adFromView ->
                adFromView.text = adFrom2
                prepareInfo.setAdFromView(adFromView)
            }
        }

        // Handle media
        render.getMediaContainer()?.let { mediaContainer ->
            val mediaView = adMaterial.getAdMediaView(mediaContainer)
            val imageList = adMaterial.imageUrlList

            val mediaLp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            if (mediaView != null) {
                (mediaView.parent as? ViewGroup)?.removeView(mediaView)
                mediaContainer.addView(mediaView, mediaLp)
            } else if (!imageList.isNullOrEmpty()) {
                val imageView = TUNativeImageView(context)
                imageView.setImage(imageList.first())
                mediaContainer.addView(imageView, mediaLp)
                prepareInfo.mainImageView = imageView
            } else if (!adMaterial.mainImageUrl.isNullOrEmpty()) {
                val imageView = TUNativeImageView(context)
                imageView.setImage(adMaterial.mainImageUrl)
                mediaContainer.addView(imageView, mediaLp)
                prepareInfo.mainImageView = imageView
            }
        }

        // Set up click views
        val clickViews = ArrayList<android.view.View>()
        rootView?.let { clickViews.add(it) }
        render.getMediaContainer()?.let { clickViews.add(it) }
        render.getTitleView()?.let { clickViews.add(it) }
        render.getDescView()?.let { clickViews.add(it) }
        render.getActionView()?.let { clickViews.add(it) }
        prepareInfo.clickViewList = clickViews

        // Handle close button
        prepareInfo.closeView = render.getCloseView()
        render.getCloseView()?.setOnClickListener {
            container.removeAllViews()
            attachedMaterial?.destory()
            attachedMaterial = null
        }

        // Create TopOn native ad view wrapper
        val toponWrap = TUNativeAdView(context)
        val lp = ViewGroup.MarginLayoutParams(
            ViewGroup.MarginLayoutParams.MATCH_PARENT,
            rootView.layoutParams?.height ?: ViewGroup.MarginLayoutParams.WRAP_CONTENT
        )
        toponWrap.layoutParams = lp
        toponWrap.addView(rootView)

        // Add to container
        container.removeAllViews()
        container.addView(toponWrap)

        nativeAd.renderAdContainer(toponWrap, rootView)

        return toponWrap
    }

    override fun destroyAd() {
        attachedMaterial?.destory()
        attachedMaterial = null
        tmpAd = null
    }
}

class TopOnRewardedAd(metadata: AdMetadata) : TopOnAds(metadata) {

    private var rewardedVideoAd: TURewardVideoAd? = null

    override fun getPlatform(): String = "TopOn"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val rewardedVideo = TURewardVideoAd(context, unitId)

        rewardedVideo.setAdListener(object : TURewardVideoListener {
            private val showCalled = AtomicBoolean(false)

            override fun onRewardedVideoAdLoaded() {
                this@TopOnRewardedAd.onLoadSuccess()
            }

            override fun onRewardedVideoAdFailed(error: com.thinkup.core.api.AdError) {
                this@TopOnRewardedAd.onLoadFailed(AdErrorC(error.code.toString(), error.desc))
            }

            override fun onRewardedVideoAdPlayStart(adInfo: TUAdInfo) {
                val revenue = adInfo.publisherRevenue
                if (showCalled.compareAndSet(false, true)) {
                    materialEcpm = adInfo.ecpm
                    putRevenue(adInfo)
                    this@TopOnRewardedAd.onShowSuccess()
                }
            }

            override fun onRewardedVideoAdPlayEnd(adInfo: TUAdInfo) {
                // Video play end callback
            }

            override fun onRewardedVideoAdPlayFailed(
                error: com.thinkup.core.api.AdError,
                adInfo: TUAdInfo
            ) {
                this@TopOnRewardedAd.onShowFailed(AdErrorC(error.code.toString(), error.desc))
            }

            override fun onRewardedVideoAdClosed(adInfo: TUAdInfo) {
                this@TopOnRewardedAd.onAdClosed()
            }

            override fun onRewardedVideoAdPlayClicked(adInfo: TUAdInfo) {
                this@TopOnRewardedAd.onAdClicked()
            }

            override fun onReward(adInfo: TUAdInfo) {
                if (showCalled.compareAndSet(false, true)) {
                    materialEcpm = adInfo.ecpm
                    putRevenue(adInfo)
                    this@TopOnRewardedAd.onShowSuccess()
                }
                this@TopOnRewardedAd.onAdRewarded()
            }
        })

        rewardedVideo.load()
        rewardedVideoAd = rewardedVideo
    }

    override fun showAd(context: Context, container: AdContainer?) {
        val ad = rewardedVideoAd
        if (ad == null || !ad.isAdReady) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Ad not ready"))
            return
        }

        val activity = context as? android.app.Activity
        if (activity == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Context is not an Activity"))
            return
        }

        ad.show(activity)
    }

    override fun destroyAd() {
//        rewardedVideoAd?.onDestroy()
        rewardedVideoAd = null
    }
}

class TopOnSplashAd(metadata: AdMetadata) : TopOnAds(metadata) {

    private var splashAd: TUSplashAd? = null

    private val splashListener = object : TUSplashAdListener {
        override fun onAdLoaded(isReady: Boolean) {
            kotlin.runCatching {
                val ad = splashAd ?: return@runCatching
                val ecpm = ad.checkAdStatus().tuTopAdInfo.ecpm
                this@TopOnSplashAd.materialEcpm = ecpm
            }
            this@TopOnSplashAd.onLoadSuccess()
        }

        override fun onAdLoadTimeout() {
            this@TopOnSplashAd.onLoadFailed(
                AdErrorC(
                    AdErrorC.ERROR_TIMEOUT,
                    "Splash ad load timeout"
                )
            )
        }

        override fun onNoAdError(error: com.thinkup.core.api.AdError?) {
            this@TopOnSplashAd.onLoadFailed(
                AdErrorC(
                    error?.code?.toString() ?: AdErrorC.ERROR_NO_FILL,
                    error?.desc ?: "No ad available"
                )
            )
        }

        override fun onAdShow(adInfo: TUAdInfo?) {
            kotlin.runCatching {
                val info = adInfo ?: return@runCatching
                val revenue = info.publisherRevenue
                materialEcpm = info.ecpm
                putRevenue(adInfo)
            }
            this@TopOnSplashAd.onShowSuccess()
        }

        override fun onAdClick(adInfo: TUAdInfo?) {
            this@TopOnSplashAd.onAdClicked()
        }

        override fun onAdDismiss(adInfo: TUAdInfo?, extraInfo: TUSplashAdExtraInfo?) {
            this@TopOnSplashAd.onAdClosed()
        }
    }

    override fun getPlatform(): String = "TopOn"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val splash = TUSplashAd(context, unitId, splashListener)
        splashAd = splash
        splash.loadAd()
    }

    override fun showAd(context: Context, container: AdContainer?) {
        val activity = context as? android.app.Activity
        if (activity == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Context is not an Activity"))
            return
        }

        val unitId = getUnitCode()
        val ad = TUSplashAd(activity, unitId, splashListener)

        if (!ad.isAdReady()) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_NOT_READY, "Ad not ready"))
            return
        }

        if (container != null) {
            ad.show(activity, container.getParent()!!)
        } else {
            val newContainer = activity.findViewById<ViewGroup>(android.R.id.content)
            ad.show(activity, newContainer)
        }
    }

    override fun destroyAd() {
        splashAd = null
    }
}
