package com.nice.library_float

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FloatPulseView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbit = RectF()
    private val orange = ContextCompat.getColor(context, R.color.color_FFFF6B2C)
    private val white = ContextCompat.getColor(context, R.color.color_FFFFFFFF)
    private var animator: ValueAnimator? = null
    private var phase = 0f
    private var requested = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(width, height) / 2f
        val pulse = (sin(phase * PI * 2).toFloat() + 1f) / 2f
        paint.style = Paint.Style.FILL
        paint.color = orange
        paint.alpha = (24 + 20 * pulse).toInt()
        canvas.drawCircle(centerX, centerY, radius * (0.94f + pulse * 0.04f), paint)
        paint.alpha = 255
        canvas.drawCircle(centerX, centerY, radius * 0.87f, paint)
        val orbitRadius = radius * 0.72f
        orbit.set(centerX - orbitRadius, centerY - orbitRadius, centerX + orbitRadius, centerY + orbitRadius)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.035f
        paint.color = white
        paint.alpha = 44
        canvas.drawOval(orbit, paint)
        paint.alpha = 180
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(orbit, phase * 360f - 90f, 76f, false, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        val angle = (phase * 360f - 90f) * PI / 180
        canvas.drawCircle(centerX + cos(angle).toFloat() * orbitRadius, centerY + sin(angle).toFloat() * orbitRadius, radius * 0.048f, paint)
    }

    fun playAnimation() {
        requested = true
        updateAnimation()
    }

    fun cancelAnimation() {
        requested = false
        animator?.cancel()
        animator = null
    }

    private fun updateAnimation() {
        val enabled = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
        if (!requested || !isAttachedToWindow || !isShown || windowVisibility != VISIBLE || !enabled) {
            animator?.cancel()
            animator = null
            return
        }
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

}
