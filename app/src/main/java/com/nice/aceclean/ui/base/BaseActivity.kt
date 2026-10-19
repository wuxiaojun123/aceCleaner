package com.nice.aceclean.ui.base

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.ad.BackInterstitialExit
import com.nice.aceclean.ad.HotStartManager
import com.nice.aceclean.util.LocaleHelper
import com.nice.aceclean.util.LogUtils
import com.nice.library_ad_sdk.AdSdkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

abstract class BaseActivity<B : ViewBinding>(
    private val inflateBinding: (LayoutInflater) -> B,
    @param:LayoutRes private val layoutResId: Int,
) : AppCompatActivity() {

    protected lateinit var binding: B
        private set

    private var systemBackBlocker: OnBackPressedCallback? = null
    private var backInterstitialExit: BackInterstitialExit? = null
    private var restoredBackExit = false

    protected val isBackExitPending: Boolean
        get() = restoredBackExit || backInterstitialExit?.requested == true

    /** Opt-in for feature pages whose toolbar and system Back share an exit ad. */
    protected fun enableBackInterstitial() {
        if (backInterstitialExit != null) return
        restoredBackExit = savedStateRegistry.consumeRestoredStateForKey(BACK_AD_STATE)
            ?.getBoolean("exit_requested") == true
        backInterstitialExit = BackInterstitialExit(
            showAd = { showBackInterstitial() },
            finish = { if (!isFinishing && !isDestroyed) finish() },
            onFailure = { LogUtils.w("back_ad_failed", BACK_AD_TAG, it) },
        )
        savedStateRegistry.registerSavedStateProvider(BACK_AD_STATE) {
            Bundle().apply { putBoolean("exit_requested", isBackExitPending) }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWithBackInterstitial()
        })
        lifecycleScope.launch {
            if (restoredBackExit) {
                // Preserve the exit request across recreation without showing another ad.
                lifecycle.withResumed {
                    if (!isFinishing && !isDestroyed) finish()
                }
                return@launch
            }
            try {
                AdSdkManager.preload(applicationContext, AppAdManager.FEATURE_BACK)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                LogUtils.w("back_ad_preload_failed", BACK_AD_TAG, error)
            }
        }
    }

    /** Show a cached ad if available, then exit; never wait for loading on Back. */
    protected fun finishWithBackInterstitial() {
        if (isFinishing || isDestroyed || isBackExitPending) return
        val exit = backInterstitialExit
        if (exit == null) {
            finish()
            return
        }
        lifecycleScope.launch { exit.request() }
    }

    private suspend fun showBackInterstitial() {
        lifecycle.withResumed { }
        if (isFinishing || isDestroyed) return
        val previousSkip = HotStartManager.skipNextHotStart
        HotStartManager.markSkipNextHotStart()
        try {
            val closed = AppAdManager.showLoaded(this, AppAdManager.FEATURE_BACK)
            LogUtils.d("back_ad_finished closed=$closed", BACK_AD_TAG)
        } finally {
            HotStartManager.skipNextHotStart = previousSkip
        }
    }

    /** Blocks the system Back button and gesture for this activity's lifecycle. */
    protected fun disableSystemBack() {
        if (systemBackBlocker != null) return
        systemBackBlocker = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        }.also { onBackPressedDispatcher.addCallback(this, it) }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    final override fun onCreate(savedInstanceState: Bundle?) {
        val navigationBarColor = ContextCompat.getColor(this, navigationBarColorRes)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(navigationBarColor, navigationBarColor),
        )
        super.onCreate(savedInstanceState)
        binding = inflateBinding(layoutInflater)
        setActivityContent(binding, layoutResId)
        initViews()
        initData()
    }

    @get:ColorRes
    protected open val navigationBarColorRes: Int = R.color.white

    /** Replaces this activity's screen while preserving the shared system-bar inset handling. */
    protected fun <T : ViewBinding> setActivityContent(contentBinding: T, @LayoutRes layoutResId: Int): T {
        setContentView(contentBinding.root)
        // MainActivity hosts a fragment whose background and content manage their own insets.
        if (layoutResId != R.layout.activity_main) SystemBarInsets.apply(contentBinding.root)
        return contentBinding
    }

    protected abstract fun initViews()

    protected open fun initData() = Unit

    private companion object {
        const val BACK_AD_TAG = "FeatureBackAd"
        const val BACK_AD_STATE = "feature_back_interstitial"
    }
}
