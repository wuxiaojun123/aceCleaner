package com.cater.bi.a

import android.content.Context
import android.view.ViewGroup
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import android.widget.ImageView
import android.widget.TextView

/**
 * AdMob ad implementations (GMA Next-Gen SDK)
 *
 * created on 2025/8/10
 * @author holmes
 */

abstract class AdmobAd(metadata: AdMetadata) : AbstractAd(metadata) {

    init {
        delayCallShowSuccessAfterPaid = true
    }

}
class AdMobBannerAd(metadata: AdMetadata) : AdmobAd(metadata) {

    private var adView: AdView? = null
    private var bannerAd: BannerAd? = null

    override fun getPlatform(): String = "AdMob"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val bannerAdView = AdView(context)
        val displayMetrics = context.resources.displayMetrics
        val adWidthDp = (displayMetrics.widthPixels / displayMetrics.density)
            .toInt()
            .coerceAtLeast(1)
        val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(
            context,
            adWidthDp
        )

        val adRequest = BannerAdRequest.Builder(unitId, adSize).build()

        bannerAdView.loadAd(
            adRequest,
            object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    handler.post {
                        bannerAd = ad
                        ad.adEventCallback = object : BannerAdEventCallback {
                            override fun onAdClicked() {
                                handler.post {
                                    this@AdMobBannerAd.onAdClicked()
                                }
                            }

                            override fun onAdImpression() {
                                handler.post {
                                    this@AdMobBannerAd.onShowSuccess()
                                }
                            }

                            override fun onAdPaid(value: AdValue) {
                                handler.post {
                                    val revenue = value.valueMicros / 1000000.0
                                    materialEcpm = revenue * 1000
                                    materialRevenue = AdRevenue(
                                        getPlatform(),
                                        getSourceNetwork(),
                                        getAdType(),
                                        getUnitCode(),
                                        revenue,
                                        value.currencyCode,
                                    )
                                    onPaid()
                                }
                            }
                        }
                        this@AdMobBannerAd.onLoadSuccess()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    handler.post {
                        this@AdMobBannerAd.onLoadFailed(
                            AdErrorC(
                                adError.code.toString(),
                                adError.message
                            )
                        )
                    }
                }
            }
        )
        adView = bannerAdView
    }

    override fun showAd(context: Context, container: AdContainer?) {
        val ad = adView
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
        adView?.destroy()
        adView = null
        bannerAd = null
    }

    override fun getSourceNetwork(): String {
        return bannerAd?.getResponseInfo()?.loadedAdSourceResponseInfo?.name ?: ""
    }
}

class AdMobInterstitialAd(metadata: AdMetadata) : AdmobAd(metadata) {

    private var interstitialAd: InterstitialAd? = null

    override fun getPlatform(): String = "AdMob"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val adRequest = AdRequest.Builder(unitId).build()

        InterstitialAd.load(
            adRequest,
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    handler.post {
                        interstitialAd = ad
                        this@AdMobInterstitialAd.onLoadSuccess()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    handler.post {
                        interstitialAd = null
                        this@AdMobInterstitialAd.onLoadFailed(
                            AdErrorC(
                                adError.code.toString(),
                                adError.message
                            )
                        )
                    }
                }
            }
        )
    }

    override fun showAd(context: Context, container: AdContainer?) {
        val ad = interstitialAd
        if (ad == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Ad not loaded"))
            return
        }

        val activity = context as? android.app.Activity
        if (activity == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Context is not an Activity"))
            return
        }

        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdClicked() {
                handler.post {
                    this@AdMobInterstitialAd.onAdClicked()
                }
            }

            override fun onAdDismissedFullScreenContent() {
                handler.post {
                    this@AdMobInterstitialAd.onAdClosed()
                }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                handler.post {
                    this@AdMobInterstitialAd.onShowFailed(
                        AdErrorC(
                            fullScreenContentError.code.toString(),
                            fullScreenContentError.message
                        )
                    )
                }
            }

            override fun onAdShowedFullScreenContent() {
                handler.post {
                    this@AdMobInterstitialAd.onShowSuccess()
                }
            }

            override fun onAdImpression() {
                handler.post {
                    this@AdMobInterstitialAd.onShowSuccess()
                }
            }

            override fun onAdPaid(value: AdValue) {
                handler.post {
                    val revenue = value.valueMicros / 1000000.0
                    materialEcpm = revenue * 1000
                    materialRevenue = AdRevenue(
                        getPlatform(),
                        getSourceNetwork(),
                        getAdType(),
                        getUnitCode(),
                        revenue,
                        value.currencyCode,
                    )
                    onPaid()
                }
            }
        }

        ad.show(activity)
    }

    override fun destroyAd() {
        interstitialAd = null
    }

    override fun getSourceNetwork(): String {
        return interstitialAd?.getResponseInfo()?.loadedAdSourceResponseInfo?.name ?: ""
    }
}

class AdMobNativeAd(metadata: AdMetadata) : AdmobAd(metadata) {

    private var nativeAd: NativeAd? = null

    override fun getPlatform(): String = "AdMob"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()

        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .build()

        val adRequest = NativeAdRequest
            .Builder(unitId, listOf(NativeAd.NativeAdType.NATIVE))
            .setVideoOptions(videoOptions)
            .setMediaAspectRatio(NativeAd.NativeMediaAspectRatio.LANDSCAPE)
            .build()

        NativeAdLoader.load(
            adRequest,
            object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    handler.post {
                        this@AdMobNativeAd.nativeAd = nativeAd
                        nativeAd.adEventCallback = object : NativeAdEventCallback {
                            override fun onAdClicked() {
                                handler.post {
                                    this@AdMobNativeAd.onAdClicked()
                                }
                            }

                            override fun onAdImpression() {
                                handler.post {
                                    this@AdMobNativeAd.onShowSuccess()
                                }
                            }

                            override fun onAdPaid(value: AdValue) {
                                handler.post {
                                    val revenue = value.valueMicros / 1000000.0
                                    materialEcpm = revenue * 1000
                                    materialRevenue = AdRevenue(
                                        getPlatform(),
                                        getSourceNetwork(),
                                        getAdType(),
                                        getUnitCode(),
                                        revenue,
                                        value.currencyCode,
                                    )
                                    onPaid()
                                }
                            }
                        }
                        this@AdMobNativeAd.onLoadSuccess()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    handler.post {
                        this@AdMobNativeAd.onLoadFailed(
                            AdErrorC(
                                adError.code.toString(),
                                adError.message
                            )
                        )
                    }
                }
            }
        )
    }

    override fun showAd(context: Context, container: AdContainer?) {
        onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Native ad requires render interface"))
    }

    override fun showAd(context: Context, container: AdContainer?, render: INativeRender?) {
        val ad = nativeAd
        if (ad == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Ad not loaded"))
            return
        }

        if (render == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Native ad requires render interface"))
            return
        }

        if (container == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Container is required"))
            return
        }

        val activity = context as? android.app.Activity
        if (activity == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Context is not an Activity"))
            return
        }

        try {
            bindNativeAdToRender(activity, container, ad, render)
        } catch (e: Exception) {
            onShowFailed(
                AdErrorC(
                    AdErrorC.ERROR_INTERNAL,
                    "Failed to bind native ad: ${e.message}"
                )
            )
        }
    }

    private fun bindNativeAdToRender(
        activity: android.app.Activity,
        container: AdContainer,
        ad: NativeAd,
        render: INativeRender
    ) {
        val rootContainer = container.getParent()
        if (rootContainer == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Container is required"))
            return
        }

        // Get the root view from render
        val rootView = render.getRoot(activity, rootContainer, ad)

        // Populate native ad data
        render.getTitleView()?.text = ad.headline
        render.getDescView()?.text = ad.body

        // Handle icon
        ad.icon?.let { icon ->
            render.getIconView()?.let { iconView ->
                if (iconView is ImageView) {
                    if (icon.drawable != null) {
                        iconView.setImageDrawable(icon.drawable)
                    } else if (icon.uri != null) {
                        // For simplicity, just set a placeholder or handle with image loading library
                        iconView.setImageURI(icon.uri)
                    }
                }
            }
        }

        // Handle media content
        var mediaView: MediaView? = null
        render.getMediaContainer()?.let { mediaContainer ->
            val view = MediaView(activity)
            view.mediaContent = ad.mediaContent
            view.imageScaleType = ImageView.ScaleType.CENTER_CROP
            mediaView = view

            mediaContainer.removeAllViews()
            mediaContainer.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        // Handle call to action
        render.getActionView()?.let { actionView ->
            if (actionView is TextView) {
                actionView.text = ad.callToAction
            }
        }

        // Handle advertiser/store info
        render.getLogoView()?.let { logoView ->
            if (logoView is TextView) {
                logoView.text = ad.advertiser ?: ad.store
            }
        }

        // Create NativeAdView and register views
        val nativeAdView = NativeAdView(activity)
        nativeAdView.headlineView = render.getTitleView()
        nativeAdView.bodyView = render.getDescView()
        nativeAdView.iconView = render.getIconView()
        nativeAdView.callToActionView = render.getActionView()
        nativeAdView.advertiserView = render.getLogoView()

        // Add root view to native ad view
        nativeAdView.addView(rootView)
        nativeAdView.registerNativeAd(ad, mediaView)

        // Add to container
        rootContainer.removeAllViews()
        rootContainer.addView(
            nativeAdView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Register close button
        render.getCloseView()?.setOnClickListener {
            onAdClosed()
            rootContainer.removeAllViews()
        }

        onShowSuccess()
    }

    override fun destroyAd() {
        nativeAd?.destroy()
        nativeAd = null
    }

    override fun getSourceNetwork(): String {
        return nativeAd?.getResponseInfo()?.loadedAdSourceResponseInfo?.name ?: ""
    }
}

class AdMobRewardedAd(metadata: AdMetadata) : AdmobAd(metadata) {

    private var rewardedAd: RewardedAd? = null

    override fun getPlatform(): String = "AdMob"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val adRequest = AdRequest.Builder(unitId).build()

        RewardedAd.load(
            adRequest,
            object : AdLoadCallback<RewardedAd> {
                override fun onAdLoaded(ad: RewardedAd) {
                    handler.post {
                        rewardedAd = ad
                        this@AdMobRewardedAd.onLoadSuccess()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    handler.post {
                        rewardedAd = null
                        this@AdMobRewardedAd.onLoadFailed(
                            AdErrorC(
                                adError.code.toString(),
                                adError.message
                            )
                        )
                    }
                }
            }
        )
    }

    override fun showAd(context: Context, container: AdContainer?) {
        val ad = rewardedAd
        if (ad == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Ad not loaded"))
            return
        }

        val activity = context as? android.app.Activity
        if (activity == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Context is not an Activity"))
            return
        }

        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdClicked() {
                handler.post {
                    this@AdMobRewardedAd.onAdClicked()
                }
            }

            override fun onAdDismissedFullScreenContent() {
                handler.post {
                    this@AdMobRewardedAd.onAdClosed()
                }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                handler.post {
                    this@AdMobRewardedAd.onShowFailed(
                        AdErrorC(
                            fullScreenContentError.code.toString(),
                            fullScreenContentError.message
                        )
                    )
                }
            }

            override fun onAdShowedFullScreenContent() {
                handler.post {
                    this@AdMobRewardedAd.onShowSuccess()
                }
            }

            override fun onAdImpression() {
                handler.post {
                    this@AdMobRewardedAd.onShowSuccess()
                }
            }

            override fun onAdPaid(value: AdValue) {
                handler.post {
                    val revenue = value.valueMicros / 1000000.0
                    materialEcpm = revenue * 1000
                    materialRevenue = AdRevenue(
                        getPlatform(),
                        getSourceNetwork(),
                        getAdType(),
                        getUnitCode(),
                        revenue,
                        value.currencyCode,
                    )
                    onPaid()
                }
            }
        }

        ad.show(activity) { rewardItem ->
            handler.post {
                this@AdMobRewardedAd.onAdRewarded()
            }
        }
    }

    override fun destroyAd() {
        rewardedAd = null
    }

    override fun getSourceNetwork(): String {
        return rewardedAd?.getResponseInfo()?.loadedAdSourceResponseInfo?.name ?: ""
    }
}

class AdMobSplashAd(metadata: AdMetadata) : AdmobAd(metadata) {

    private var appOpenAd: AppOpenAd? = null

    override fun getPlatform(): String = "AdMob"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val adRequest = AdRequest.Builder(unitId).build()

        AppOpenAd.load(
            adRequest,
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    handler.post {
                        appOpenAd = ad
                        this@AdMobSplashAd.onLoadSuccess()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    handler.post {
                        appOpenAd = null
                        this@AdMobSplashAd.onLoadFailed(
                            AdErrorC(
                                adError.code.toString(),
                                adError.message
                            )
                        )
                    }
                }
            }
        )
    }

    override fun showAd(context: Context, container: AdContainer?) {
        val ad = appOpenAd
        if (ad == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Ad not loaded"))
            return
        }

        val activity = context as? android.app.Activity
        if (activity == null) {
            onShowFailed(AdErrorC(AdErrorC.ERROR_INTERNAL, "Context is not an Activity"))
            return
        }

        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdClicked() {
                handler.post {
                    this@AdMobSplashAd.onAdClicked()
                }
            }

            override fun onAdDismissedFullScreenContent() {
                handler.post {
                    this@AdMobSplashAd.onAdClosed()
                }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                handler.post {
                    this@AdMobSplashAd.onShowFailed(AdErrorC(fullScreenContentError.code.toString(), fullScreenContentError.message))
                }
            }

            override fun onAdShowedFullScreenContent() {
                handler.post {
                    this@AdMobSplashAd.onShowSuccess()
                }
            }

            override fun onAdImpression() {
                handler.post {
                    this@AdMobSplashAd.onShowSuccess()
                }
            }

            override fun onAdPaid(value: AdValue) {
                handler.post {
                    val revenue = value.valueMicros / 1000000.0
                    materialEcpm = revenue * 1000
                    materialRevenue = AdRevenue(
                        getPlatform(),
                        getSourceNetwork(),
                        getAdType(),
                        getUnitCode(),
                        revenue,
                        value.currencyCode,
                    )
                    onPaid()
                }
            }
        }

        ad.show(activity)
    }

    override fun destroyAd() {
        appOpenAd = null
    }

    override fun getSourceNetwork(): String {
        return appOpenAd?.getResponseInfo()?.loadedAdSourceResponseInfo?.name ?: ""
    }
}
