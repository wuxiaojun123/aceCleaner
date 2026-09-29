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
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseActivity

class SplashActivity : BaseActivity(R.layout.activity_splash) {

    override val statusBarColorRes: Int = R.color.white

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
        progressBar = view(R.id.splash_progress)
        loadingText = view(R.id.splash_loading)
    }

    override fun initData() {
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
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_DURATION_MS = 1_450L
    }
}
