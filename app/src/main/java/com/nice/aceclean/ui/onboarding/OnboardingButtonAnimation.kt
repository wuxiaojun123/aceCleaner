package com.nice.aceclean.ui.onboarding

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The same breathing and shine treatment used on the reference guide's action buttons. */
internal class OnboardingButtonAnimation(owner: LifecycleOwner, button: View, shine: View) : AutoCloseable {
    private val job: Job = owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val scaleX = ObjectAnimator.ofFloat(button, View.SCALE_X, 1f, 1.03f)
            val scaleY = ObjectAnimator.ofFloat(button, View.SCALE_Y, 1f, 1.03f)
            val alpha = ObjectAnimator.ofFloat(button, View.ALPHA, 1f, 0.92f)
            val breathing = listOf(scaleX, scaleY, alpha)
            breathing.forEach {
                it.duration = 1_100L
                it.repeatCount = ValueAnimator.INFINITE
                it.repeatMode = ValueAnimator.REVERSE
                it.start()
            }
            var sweep: ObjectAnimator? = null
            try {
                while (true) {
                    if (button.width > 0 && button.isEnabled) {
                        val bandWidth = (button.width * 0.12f).toInt().coerceAtLeast(1)
                        shine.layoutParams = shine.layoutParams.apply { width = bandWidth }
                        shine.visibility = View.VISIBLE
                        sweep = ObjectAnimator.ofFloat(shine, View.TRANSLATION_X,
                            -bandWidth - 24 * button.resources.displayMetrics.density,
                            button.width + 24 * button.resources.displayMetrics.density).apply {
                            duration = 1_200L
                            interpolator = AccelerateDecelerateInterpolator()
                            start()
                        }
                        delay(1_200L)
                        shine.visibility = View.INVISIBLE
                    }
                    delay(2_800L)
                }
            } finally {
                breathing.forEach { it.cancel() }
                sweep?.cancel()
                button.scaleX = 1f
                button.scaleY = 1f
                button.alpha = if (button.isEnabled) 1f else 0.45f
                shine.visibility = View.INVISIBLE
            }
        }
    }
    override fun close() = job.cancel()
}
