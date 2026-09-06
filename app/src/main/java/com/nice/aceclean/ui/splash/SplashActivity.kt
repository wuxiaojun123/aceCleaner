package com.nice.aceclean.ui.splash

import android.animation.ValueAnimator
import android.content.Intent
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.animation.doOnEnd
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.ui.main.MainActivity

class SplashActivity : BaseActivity(R.layout.activity_splash) {

    override val statusBarColorRes: Int = R.color.white

    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private var splashAnimator: ValueAnimator? = null

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
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
            start()
        }
    }

    override fun onDestroy() {
        splashAnimator?.cancel()
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_DURATION_MS = 1_450L
    }
}
