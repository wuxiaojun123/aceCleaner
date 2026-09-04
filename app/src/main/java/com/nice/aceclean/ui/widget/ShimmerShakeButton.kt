package com.nice.aceclean.ui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/** A lightweight CTA animation: shimmer first, then a short horizontal shake every five seconds. */
class ShimmerShakeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shimmerClipPath = Path()
    private val shimmerBounds = RectF()
    private val cornerRadius = 30f * resources.displayMetrics.density
    private var shimmerProgress = START_PROGRESS
    private var runningAnimator: AnimatorSet? = null

    private val nextCycle = Runnable { startCycle() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(nextCycle)
        postDelayed(nextCycle, INITIAL_DELAY_MS)
    }

    override fun onDetachedFromWindow() {
        runningAnimator?.removeAllListeners()
        runningAnimator?.cancel()
        runningAnimator = null
        removeCallbacks(nextCycle)
        translationX = 0f
        shimmerProgress = START_PROGRESS
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (shimmerProgress > START_PROGRESS && width > 0 && height > 0) {
            val bandWidth = width * SHIMMER_WIDTH_RATIO
            val centerX = shimmerProgress * (width + bandWidth) - bandWidth
            shimmerPaint.shader = LinearGradient(
                centerX - bandWidth,
                height.toFloat(),
                centerX + bandWidth,
                0f,
                intArrayOf(Color.TRANSPARENT, SHIMMER_EDGE, SHIMMER_CENTER, SHIMMER_EDGE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
            val saveCount = canvas.save()
            shimmerBounds.set(0f, 0f, width.toFloat(), height.toFloat())
            shimmerClipPath.reset()
            shimmerClipPath.addRoundRect(shimmerBounds, cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.clipPath(shimmerClipPath)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shimmerPaint)
            canvas.restoreToCount(saveCount)
        }
        super.onDraw(canvas)
    }

    @Suppress("unused")
    fun setShimmerProgress(progress: Float) {
        shimmerProgress = progress
        invalidate()
    }

    private fun startCycle() {
        if (!isAttachedToWindow || !isShown) {
            scheduleNext(CYCLE_MS)
            return
        }
        val distance = SHAKE_DISTANCE_DP * resources.displayMetrics.density
        val shimmer = ObjectAnimator.ofFloat(this, "shimmerProgress", START_PROGRESS, END_PROGRESS).apply {
            duration = SHIMMER_DURATION_MS
        }
        val shake = ObjectAnimator.ofFloat(
            this,
            TRANSLATION_X,
            0f,
            -distance,
            distance,
            -distance * 0.75f,
            distance * 0.75f,
            -distance * 0.4f,
            distance * 0.4f,
            0f,
        ).apply {
            duration = SHAKE_DURATION_MS
        }
        runningAnimator = AnimatorSet().apply {
            playSequentially(shimmer, shake)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    shimmerProgress = START_PROGRESS
                    invalidate()
                    runningAnimator = null
                    scheduleNext((CYCLE_MS - SHIMMER_DURATION_MS - SHAKE_DURATION_MS).coerceAtLeast(0L))
                }
            })
            start()
        }
    }

    private fun scheduleNext(delayMs: Long) {
        removeCallbacks(nextCycle)
        if (isAttachedToWindow) postDelayed(nextCycle, delayMs)
    }

    private companion object {
        const val INITIAL_DELAY_MS = 800L
        const val CYCLE_MS = 5_000L
        const val SHIMMER_DURATION_MS = 900L
        const val SHAKE_DURATION_MS = 420L
        const val SHAKE_DISTANCE_DP = 5f
        const val SHIMMER_WIDTH_RATIO = 0.22f
        const val START_PROGRESS = -0.1f
        const val END_PROGRESS = 1.1f
        const val SHIMMER_EDGE = 0x20FFFFFF
        const val SHIMMER_CENTER = 0xA8FFFFFF.toInt()
    }
}
