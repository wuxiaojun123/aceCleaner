package com.nice.aceclean.ui.main

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.ad.HotStartManager
import com.nice.aceclean.databinding.ActivitySplashBinding
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SplashActivity : BaseActivity<ActivitySplashBinding>({ ActivitySplashBinding.inflate(it) }, R.layout.activity_splash) {


    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private var splashAnimator: ValueAnimator? = null
    private var navigationStarted = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        openNextScreen()
    }

    override fun initViews() {
        progressBar = binding.splashProgress
        loadingText = binding.splashLoading
    }

    override fun initData() {
        HotStartManager.markSplashAdStarted()
        if (AppAdManager.splashPlacementsConfigured) {
            startAdFlow()
            return
        }
        splashAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = SPLASH_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Int
                progressBar.progress = progress
                loadingText.text = getString(R.string.loading, progress)
            }
            doOnEnd {
                if (!isFinishing && !isDestroyed) requestNotificationPermissionIfNeeded()
            }
            start()
        }
    }

    private fun startAdFlow() {
        val placement = if (intent.getBooleanExtra(EXTRA_HOT_START, false)) {
            AppAdManager.HOT_START
        } else {
            AppAdManager.COLD_START
        }
        splashAnimator = ValueAnimator.ofInt(0, 99).apply {
            duration = AppAdManager.LOAD_TIMEOUT_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Int
                progressBar.progress = progress
                loadingText.text = getString(R.string.loading, progress)
            }
            start()
        }

        lifecycleScope.launch {
            try {
                val loaded = AppAdManager.preload(this@SplashActivity, placement)
                splashAnimator?.cancel()
                progressBar.progress = 100
                loadingText.text = getString(R.string.loading, 100)
                if (loaded) {
                    AppAdManager.showLoaded(this@SplashActivity, placement)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                LogUtils.w(tag = TAG, msg = "Startup ad failed for placement $placement", throwable = error)
            } finally {
                if (!isFinishing && !isDestroyed) requestNotificationPermissionIfNeeded()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNextScreen()
        }
    }

    private fun openNextScreen() {
        if (navigationStarted || isFinishing || isDestroyed) return
        navigationStarted = true
        val target = if (DeviceScanActivity.shouldShow(this)) {
            DeviceScanActivity::class.java
        } else {
            MainActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }

    override fun onDestroy() {
        splashAnimator?.removeAllListeners()
        splashAnimator?.cancel()
        HotStartManager.markHotStartAdFinished()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_HOT_START = "extra_hot_start"
        private const val TAG = "SplashActivity"
        private const val SPLASH_DURATION_MS = 1_450L
    }
}
