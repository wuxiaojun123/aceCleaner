package com.nice.aceclean.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Draws a checkmark progressively with [PathMeasure] when a speed test succeeds. */
class CheckmarkAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF35B978")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val checkPath = Path()
    private val drawnPath = Path()
    private val pathMeasure = PathMeasure()
    private var revealProgress = 0f
    private var animator: ValueAnimator? = null

    fun play() {
        animator?.cancel()
        revealProgress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3_000L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                revealProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val side = minOf(w, h).toFloat()
        val offsetX = (w - side) / 2f
        val offsetY = (h - side) / 2f
        paint.strokeWidth = side * 0.12f
        checkPath.reset()
        checkPath.moveTo(offsetX + side * 0.22f, offsetY + side * 0.53f)
        checkPath.lineTo(offsetX + side * 0.43f, offsetY + side * 0.73f)
        checkPath.lineTo(offsetX + side * 0.79f, offsetY + side * 0.30f)
        pathMeasure.setPath(checkPath, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawnPath.reset()
        pathMeasure.getSegment(0f, pathMeasure.length * revealProgress, drawnPath, true)
        canvas.drawPath(drawnPath, paint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
