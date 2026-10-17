package com.nice.aceclean.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.nice.aceclean.R

/** Static alpha-mask shadow, matching the phone cutout rather than its rectangular bounds. */
class DeviceDetailPhoneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private var shadow: Bitmap? = null
    private var shadowSource: Drawable? = null
    private var shadowX = 0f
    private var shadowY = 0f
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_337D310E)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shadow = null
    }

    override fun onDraw(canvas: Canvas) {
        val image = drawable
        if (image != null && width > 0 && height > 0 && (shadow == null || shadowSource !== image)) {
            val density = resources.displayMetrics.density
            val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val sourceCanvas = Canvas(source)
            sourceCanvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
            sourceCanvas.concat(imageMatrix)
            image.draw(sourceCanvas)
            val offsets = IntArray(2)
            shadow = source.extractAlpha(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                maskFilter = BlurMaskFilter(12f * density, BlurMaskFilter.Blur.NORMAL)
            }, offsets)
            source.recycle()
            shadowX = offsets[0].toFloat()
            shadowY = offsets[1] + 18f * density
            shadowSource = image
        }
        shadow?.let { canvas.drawBitmap(it, shadowX, shadowY, shadowPaint) }
        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        shadow = null
        shadowSource = null
        super.onDetachedFromWindow()
    }
}
