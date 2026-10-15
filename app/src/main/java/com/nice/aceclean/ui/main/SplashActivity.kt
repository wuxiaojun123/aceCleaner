package com.nice.aceclean.ui.main

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.animation.DecelerateInterpolator
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.ad.HotStartManager
import com.nice.aceclean.databinding.ActivitySplashBinding
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.ui.onboarding.OnboardingNavigator
import com.nice.aceclean.ui.onboarding.OnboardingState
import com.nice.aceclean.ui.onboarding.OnboardingStep
import com.nice.aceclean.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity<ActivitySplashBinding>(
    { ActivitySplashBinding.inflate(it) }, R.layout.activity_splash,
) {
    override val navigationBarColorRes: Int = R.color.color_FFFAF9F7
    private var splashAnimator: ValueAnimator? = null
    private var navigating = false
    private val permissionResult = CompletableDeferred<Unit>()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionResult.complete(Unit)
    }

    override fun initViews() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        // 暂停首次启动的悬浮窗引导，直接显示原有 Splash 页面。
        // 恢复此入口时，同时恢复 OnboardingStep.NOT_STARTED 的 OVERLAY 路由。
        // if (OnboardingState.get(this) == OnboardingStep.NOT_STARTED) {
        //     startActivity(OnboardingNavigator.intent(this, OnboardingStep.OVERLAY))
        //     finish()
        //     return
        // }
        renderProgress(0)
    }

    override fun initData() {
        if (isFinishing) return
        HotStartManager.markSplashAdStarted()
        if (!intent.getBooleanExtra(EXTRA_FROM_ONBOARDING, false) && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else permissionResult.complete(Unit)
        val placement = startupPlacement()
        val adsConfigured = AppAdManager.splashPlacementsConfigured
        splashAnimator = ValueAnimator.ofInt(0, if (adsConfigured) 99 else 100).apply {
            duration = if (adsConfigured) AppAdManager.LOAD_TIMEOUT_MS else SPLASH_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { renderProgress(it.animatedValue as Int) }
            start()
        }
        lifecycleScope.launch {
            val preload = async {
                if (adsConfigured) AppAdManager.preload(this@SplashActivity, placement)
                else { delay(SPLASH_DURATION_MS); false }
            }
            permissionResult.await()
            val loaded = preload.await()
            splashAnimator?.cancel()
            renderProgress(100)
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            if (loaded) {
                try { AppAdManager.showLoaded(this@SplashActivity, placement) }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    LogUtils.w(tag = "SplashActivity", msg = "Startup ad failed for $placement", throwable = error)
                }
            }
            openNextScreen()
        }
    }

    private fun startupPlacement(): String {
        if (intent.getBooleanExtra(EXTRA_HOT_START, false)) return AppAdManager.HOT_START
        val prefs = getSharedPreferences("onboarding", MODE_PRIVATE)
        if (OnboardingState.get(this) != OnboardingStep.COMPLETED && !prefs.getBoolean("first_splash_consumed", false)) {
            prefs.edit().putBoolean("first_splash_consumed", true).apply()
            return AppAdManager.FIRST_START
        }
        return AppAdManager.COLD_START
    }

    private fun renderProgress(progress: Int) {
        binding.splashProgress.progress = progress
        binding.splashLoading.text = getString(R.string.loading, progress)
    }

    private fun openNextScreen() {
        if (navigating || isFinishing || isDestroyed) return
        navigating = true
        val next = OnboardingState.get(this).restored(intent.getBooleanExtra(EXTRA_HOT_START, false))
        val target = OnboardingNavigator.intent(this, next)
        startActivity(target)
        finish()
    }

    override fun onDestroy() {
        splashAnimator?.removeAllListeners()
        splashAnimator?.cancel()
        splashAnimator = null
        HotStartManager.markHotStartAdFinished()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_HOT_START = "extra_hot_start"
        const val EXTRA_FROM_ONBOARDING = "extra_from_onboarding"
        private const val SPLASH_DURATION_MS = 1_450L
    }
}
