package com.nice.aceclean.ui.onboarding

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.view.LayoutInflater
import android.view.Gravity
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.LayoutRes
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.viewbinding.ViewBinding
import com.nice.aceclean.ad.AppAdManager
import com.airbnb.lottie.LottieAnimationView
import com.nice.aceclean.R
import com.nice.aceclean.ad.HotStartManager
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.ui.main.MainActivity
import com.nice.aceclean.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Shared window, navigation and ad mechanics. Each screen owns its binding and business flow. */
abstract class BaseOnboardingActivity<B : ViewBinding>(
    inflate: (LayoutInflater) -> B,
    @LayoutRes layout: Int,
) : BaseActivity<B>(inflate, layout) {
    internal abstract val onboardingStep: OnboardingStep
    protected open val lightStatusBarIcons = false
    protected var navigating = false
        private set
    private var animator: ValueAnimator? = null
    private var guideDialog: Dialog? = null
    private val animations = mutableListOf<LottieAnimationView>()

    override val navigationBarColorRes: Int = R.color.color_FFFAF9F7

    /** Register explicit binding views; pause motion while ads or settings cover the page. */
    protected fun trackOnboardingAnimation(view: LottieAnimationView) {
        animations += view
    }

    override fun onStart() {
        super.onStart()
        animations.forEach { if (it.repeatCount != 0 || it.progress < 1f) it.resumeAnimation() }
    }

    override fun onStop() {
        animations.forEach { it.pauseAnimation() }
        super.onStop()
    }

    final override fun initViews() {
        OnboardingState.set(this, if (onboardingStep == OnboardingStep.COMPLETE) OnboardingStep.COMPLETED else onboardingStep)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !lightStatusBarIcons
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onGuideBackPressed()
        })
        initOnboardingViews()
    }

    protected abstract fun initOnboardingViews()
    protected open fun onGuideBackPressed() = Unit

    protected fun navigateTo(destination: Class<out Activity>) = navigateTo(Intent(this, destination))

    protected fun navigateTo(intent: Intent) {
        if (navigating || isFinishing || isDestroyed) return
        navigating = true
        startActivity(intent)
        finish()
    }

    protected fun openHome() {
        OnboardingState.set(this, OnboardingStep.COMPLETED)
        FirstRunJunkSession.result = null
        navigateTo(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    protected suspend fun awaitResumed() {
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
    }

    protected suspend fun showAd(placement: String) {
        awaitResumed()
        HotStartManager.markSplashAdStarted()
        try {
            AppAdManager.showLoaded(this, placement)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LogUtils.w(tag = javaClass.simpleName, msg = "Unable to show onboarding ad $placement", throwable = error)
        } finally {
            HotStartManager.markHotStartAdFinished()
        }
    }

    protected fun animateProgress(durationMs: Long, render: (Int) -> Unit) {
        stopProgress()
        animator = ValueAnimator.ofInt(0, 100).apply {
            duration = durationMs
            interpolator = android.view.animation.DecelerateInterpolator(1.25f)
            addUpdateListener { render(it.animatedValue as Int) }
            start()
        }
    }

    protected fun stopProgress() {
        animator?.cancel()
        animator = null
    }

    protected fun showGuideDialog(content: ViewBinding) {
        dismissGuideDialog()
        guideDialog = Dialog(this).apply {
            setContentView(content.root)
            setCancelable(false)
            show()
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.apply {
                setGravity(Gravity.BOTTOM)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setDimAmount(0.30f)
                setWindowAnimations(R.style.OnboardingSheetAnimation)
                navigationBarColor = ContextCompat.getColor(this@BaseOnboardingActivity, R.color.white)
                WindowInsetsControllerCompat(this, decorView).isAppearanceLightNavigationBars = true
            }
        }
    }

    protected fun dismissGuideDialog() {
        guideDialog?.dismiss()
        guideDialog = null
    }

    override fun onDestroy() {
        stopProgress()
        animations.forEach { it.cancelAnimation() }
        animations.clear()
        dismissGuideDialog()
        super.onDestroy()
    }
}
