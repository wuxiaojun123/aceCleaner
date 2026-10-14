package com.cater.bi.a

import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MediaAspectRatio
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import android.widget.ImageView
import android.widget.TextView

/**
 * AdMob ad implementations
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

    override fun getPlatform(): String = "AdMob"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val bannerAdView = AdView(context)
        bannerAdView.adUnitId = unitId
        val displayMetrics = context.resources.displayMetrics
        val adWidthDp = (displayMetrics.widthPixels / displayMetrics.density)
            .toInt()
            .coerceAtLeast(1)
        bannerAdView.setAdSize(
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                context,
                adWidthDp
            )
        )

        bannerAdView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                this@AdMobBannerAd.onLoadSuccess()
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                this@AdMobBannerAd.onLoadFailed(
                    AdErrorC(
                        loadAdError.code.toString(),
                        loadAdError.message
                    )
                )
            }

            override fun onAdClicked() {
                super.onAdClicked()
                this@AdMobBannerAd.onAdClicked()
            }

            override fun onAdImpression() {
                super.onAdImpression()
                this@AdMobBannerAd.onShowSuccess()
            }
        }

        bannerAdView.setOnPaidEventListener { adValue ->
            val revenue = adValue.valueMicros / 1000000.0
            materialEcpm = revenue * 1000
            materialRevenue = AdRevenue(
                getPlatform(),
                getSourceNetwork(),
                getAdType(),
                bannerAdView?.adUnitId ?: "",
                revenue,
                adValue.currencyCode,
            )
            onPaid()
        }

        val adRequest = AdRequest.Builder().build()
        bannerAdView.loadAd(adRequest)
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
    }

    override fun getSourceNetwork(): String {
        return adView?.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: ""
    }
}

class AdMobInterstitialAd(metadata: AdMetadata) : AdmobAd(metadata) {

    private var interstitialAd: InterstitialAd? = null

    override fun getPlatform(): String = "AdMob"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            unitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    this@AdMobInterstitialAd.onLoadSuccess()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    this@AdMobInterstitialAd.onLoadFailed(
                        AdErrorC(
                            loadAdError.code.toString(),
                            loadAdError.message
                        )
                    )
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

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                this@AdMobInterstitialAd.onAdClicked()
            }

            override fun onAdDismissedFullScreenContent() {
                this@AdMobInterstitialAd.onAdClosed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                this@AdMobInterstitialAd.onShowFailed(
                    AdErrorC(
                        adError.code.toString(),
                        adError.message
                    )
                )
            }

            override fun onAdShowedFullScreenContent() {
                this@AdMobInterstitialAd.onShowSuccess()
            }

            override fun onAdImpression() {
                this@AdMobInterstitialAd.onShowSuccess()
            }
        }

        ad.setOnPaidEventListener { adValue ->
            val revenue = adValue.valueMicros / 1000000.0
            materialEcpm = revenue * 1000
            materialRevenue = AdRevenue(
                getPlatform(),
                getSourceNetwork(),
                getAdType(),
                ad?.adUnitId ?: "",
                revenue,
                adValue.currencyCode,
            )
            onPaid()
        }

        ad.show(activity)
    }

    override fun destroyAd() {
        interstitialAd = null
    }

    override fun getSourceNetwork(): String {
        return interstitialAd?.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: ""
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

        val adOptions = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .setMediaAspectRatio(MediaAspectRatio.LANDSCAPE)
            .build()

        val adLoader = AdLoader.Builder(context, unitId)
            .forNativeAd { ad ->
                nativeAd = ad
                this@AdMobNativeAd.onLoadSuccess()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    this@AdMobNativeAd.onLoadFailed(
                        AdErrorC(
                            loadAdError.code.toString(),
                            loadAdError.message
                        )
                    )
                }

                override fun onAdClicked() {
                    this@AdMobNativeAd.onAdClicked()
                }

                override fun onAdImpression() {
                    this@AdMobNativeAd.onShowSuccess()
                }
            })
            .withNativeAdOptions(adOptions)
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
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
        // Set up revenue tracking
        ad.setOnPaidEventListener { adValue ->
            val revenue = adValue.valueMicros / 1000000.0
            materialEcpm = revenue * 1000
            materialRevenue = AdRevenue(
                getPlatform(),
                getSourceNetwork(),
                getAdType(),
                "",
                revenue,
                adValue.currencyCode,
            )
            onPaid()
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
        render.getMediaContainer()?.let { mediaContainer ->
            val mediaView = MediaView(activity)
            mediaView.setMediaContent(ad.mediaContent)
            mediaView.setImageScaleType(ImageView.ScaleType.CENTER_CROP)

            mediaContainer.removeAllViews()
            mediaContainer.addView(
                mediaView,
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
        render.getMediaContainer()?.let { mediaContainer ->
            if (mediaContainer.childCount > 0 && mediaContainer.getChildAt(0) is MediaView) {
                nativeAdView.mediaView = mediaContainer.getChildAt(0) as MediaView
            }
        }

        // Add root view to native ad view
        nativeAdView.addView(rootView)
        nativeAdView.setNativeAd(ad)

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
        return nativeAd?.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: ""
    }
}

class AdMobRewardedAd(metadata: AdMetadata) : AdmobAd(metadata) {

    private var rewardedAd: RewardedAd? = null

    override fun getPlatform(): String = "AdMob"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            unitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    this@AdMobRewardedAd.onLoadSuccess()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    this@AdMobRewardedAd.onLoadFailed(
                        AdErrorC(
                            loadAdError.code.toString(),
                            loadAdError.message
                        )
                    )
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

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                this@AdMobRewardedAd.onAdClicked()
            }

            override fun onAdDismissedFullScreenContent() {
                this@AdMobRewardedAd.onAdClosed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                this@AdMobRewardedAd.onShowFailed(
                    AdErrorC(
                        adError.code.toString(),
                        adError.message
                    )
                )
            }

            override fun onAdShowedFullScreenContent() {
                this@AdMobRewardedAd.onShowSuccess()
            }

            override fun onAdImpression() {
                this@AdMobRewardedAd.onShowSuccess()
            }
        }

        ad.setOnPaidEventListener { adValue ->
            val revenue = adValue.valueMicros / 1000000.0
            materialEcpm = revenue * 1000
            materialRevenue = AdRevenue(
                getPlatform(),
                getSourceNetwork(),
                getAdType(),
                ad?.adUnitId ?: "",
                revenue,
                adValue.currencyCode,
            )
            onPaid()
        }

        ad.show(activity) { rewardItem ->
            this@AdMobRewardedAd.onAdRewarded()
        }
    }

    override fun destroyAd() {
        rewardedAd = null
    }

    override fun getSourceNetwork(): String {
        return rewardedAd?.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: ""
    }
}

class AdMobSplashAd(metadata: AdMetadata) : AdmobAd(metadata) {

    private var appOpenAd: AppOpenAd? = null

    override fun getPlatform(): String = "AdMob"

    override fun loadAd(context: Context) {
        val unitId = getUnitCode()
        val adRequest = AdRequest.Builder().build()

        AppOpenAd.load(
            context,
            unitId,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    this@AdMobSplashAd.onLoadSuccess()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    appOpenAd = null
                    this@AdMobSplashAd.onLoadFailed(
                        AdErrorC(
                            loadAdError.code.toString(),
                            loadAdError.message
                        )
                    )
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

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                this@AdMobSplashAd.onAdClicked()
            }

            override fun onAdDismissedFullScreenContent() {
                this@AdMobSplashAd.onAdClosed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                this@AdMobSplashAd.onShowFailed(AdErrorC(adError.code.toString(), adError.message))
            }

            override fun onAdShowedFullScreenContent() {
                this@AdMobSplashAd.onShowSuccess()
            }

            override fun onAdImpression() {
                this@AdMobSplashAd.onShowSuccess()
            }
        }

        ad.setOnPaidEventListener { adValue ->
            val revenue = adValue.valueMicros / 1000000.0
            materialEcpm = revenue * 1000
            materialRevenue = AdRevenue(
                getPlatform(),
                getSourceNetwork(),
                getAdType(),
                ad?.adUnitId ?: "",
                revenue,
                adValue.currencyCode,
            )
            onPaid()
        }

        ad.show(activity)
    }

    override fun destroyAd() {
        appOpenAd = null
    }

    override fun getSourceNetwork(): String {
        return appOpenAd?.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: ""
    }
}
