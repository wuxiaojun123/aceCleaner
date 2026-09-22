package com.nice.aceclean.ui.widget

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.withStyledAttributes
import com.nice.aceclean.R
import kotlin.math.min

/** A rounded circular progress ring used by the network speed test. */
class SpeedTestGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private var progressColor = Color.parseColor("#FF6575F6")
    private var trackColor = Color.parseColor("#FFE5E8FA")
    private var strokeWidth = 14f * density
    private var startAngle = -90f
    private var animator: ValueAnimator? = null
    private val arcBounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, MAX_PROGRESS)
            invalidate()
        }

    init {
        context.withStyledAttributes(attrs, R.styleable.SpeedTestGaugeView) {
            progressColor = getColor(R.styleable.SpeedTestGaugeView_gaugeProgressColor, progressColor)
            trackColor = getColor(R.styleable.SpeedTestGaugeView_gaugeTrackColor, trackColor)
            strokeWidth = getDimension(R.styleable.SpeedTestGaugeView_gaugeStrokeWidth, strokeWidth)
            startAngle = getFloat(R.styleable.SpeedTestGaugeView_gaugeStartAngle, startAngle)
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun animateTo(target: Int, onEnd: (() -> Unit)? = null) {
        animator?.cancel()
        animator = ValueAnimator.ofInt(progress, target.coerceIn(0, MAX_PROGRESS)).apply {
            duration = ANIMATION_DURATION_MILLIS
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Int }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) onEnd?.invoke()
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = min(width, height).toFloat() - strokeWidth
        val left = (width - diameter) / 2f
        val top = (height - diameter) / 2f
        arcBounds.set(left, top, left + diameter, top + diameter)
        paint.strokeWidth = strokeWidth
        paint.color = trackColor
        canvas.drawArc(arcBounds, 0f, 360f, false, paint)
        if (progress > 0) {
            paint.color = progressColor
            canvas.drawArc(arcBounds, startAngle, 360f * progress / MAX_PROGRESS, false, paint)
        }
    }

    private companion object {
        const val MAX_PROGRESS = 100
        const val ANIMATION_DURATION_MILLIS = 420L
    }
}
