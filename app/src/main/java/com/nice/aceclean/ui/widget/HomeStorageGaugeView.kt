package com.nice.aceclean.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Storage ring used by the reference home layout. */
class HomeStorageGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var usedPercent: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 17f * density
        color = Color.rgb(237, 243, 245)
    }
    private val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 17f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(17, 124, 249)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(17, 124, 249)
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = size / 2f - 20f * density
        oval.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawCircle(centerX, centerY, radius, track)
        canvas.drawArc(oval, -109f, 3.6f * usedPercent, false, progress)
        val spots = arrayOf(
            floatArrayOf(-0.87f, -0.83f, 3f),
            floatArrayOf(0.94f, -0.60f, 4f),
            floatArrayOf(-0.80f, 0.83f, 1.6f),
            floatArrayOf(0.68f, 0.87f, 1.6f),
        )
        spots.forEach { spot ->
            canvas.drawCircle(
                centerX + radius * spot[0],
                centerY + radius * spot[1],
                spot[2] * density,
                dot,
            )
        }
    }
}
