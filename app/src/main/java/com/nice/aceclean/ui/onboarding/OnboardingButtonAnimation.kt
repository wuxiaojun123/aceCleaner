package com.nice.aceclean.ui.onboarding

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A quiet sweep keeps the orange action prominent without resizing its touch target. */
internal class OnboardingButtonAnimation(owner: LifecycleOwner, button: View, shine: View) : AutoCloseable {
    private val job: Job = owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var sweep: ObjectAnimator? = null
            try {
                while (true) {
                    if (button.width > 0 && button.isEnabled) {
                        val bandWidth = (button.width * 0.12f).toInt().coerceAtLeast(1)
                        shine.layoutParams = shine.layoutParams.apply { width = bandWidth }
                        shine.alpha = 0.18f
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
                    delay(3_800L)
                }
            } finally {
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
