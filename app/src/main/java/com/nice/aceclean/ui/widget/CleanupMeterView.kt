package com.nice.aceclean.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Static, softly layered meter artwork from system-cleaning.html. */
class CleanupMeterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private var artwork: Bitmap? = null
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) { artwork = null; return }
        val density = resources.displayMetrics.density
        val artWidth = min(w.toFloat(), min(340f * density, h * 340f / 360f))
        val artHeight = artWidth * 360f / 340f
        // Rasterize once on a software canvas so the reference's soft masks also work on older APIs.
        artwork = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.translate((w - artWidth) / 2f, (h - artHeight) / 2f)
            canvas.scale(artWidth / 365f, artHeight / 310f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            fun colors(vararg values: String) = values.map(Color::parseColor).toIntArray()
            fun oval(cx: Float, cy: Float, rx: Float, ry: Float, shader: Shader, alpha: Float, blur: Float = 0f) {
                paint.shader = shader
                paint.alpha = (alpha * 255).toInt()
                paint.maskFilter = if (blur > 0f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
                canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint)
            }
            val outer = LinearGradient(10f, 14f, 354f, 294f,
                colors("#d9edff", "#83baff", "#6daff7", "#cce5ff"), floatArrayOf(0f, .28f, .63f, 1f), Shader.TileMode.CLAMP)
            oval(183f, 152f, 177f, 145f, RadialGradient(183f, 135f, 200f,
                colors("#82baff", "#90c3ff", "#e0efff"), floatArrayOf(0f, .66f, 1f), Shader.TileMode.CLAMP), .32f, 6f)
            oval(182f, 154f, 172f, 140f, outer, .54f)
            oval(190f, 157f, 153f, 122f, LinearGradient(74f, 35f, 306f, 279f,
                colors("#c5e2ff", "#65a9f3", "#83bbfa", "#b5daff"), floatArrayOf(0f, .35f, .68f, 1f), Shader.TileMode.CLAMP), .64f)
            oval(173f, 164f, 148f, 119f, outer, .47f)
            // Normalize the radial gradients to each ellipse's bounding box, as in SVG.
            canvas.save()
            canvas.translate(185f, 165f)
            canvas.scale(132f, 105f)
            oval(0f, 0f, 1f, 1f, RadialGradient(0f, -.08f, 1.18f,
                colors("#d8edff", "#a5d2ff", "#61a9f4", "#418fe6"), floatArrayOf(0f, .61f, .87f, 1f), Shader.TileMode.CLAMP), .8f)
            canvas.restore()
            canvas.save()
            canvas.translate(176f, 165f)
            canvas.scale(117f, 94f)
            oval(0f, 0f, 1f, 1f, RadialGradient(0f, -.22f, 1.26f,
                colors("#e3f2ff", "#e0f0ff", "#c8e4ff", "#9dceff", "#6dacfa"), floatArrayOf(0f, .47f, .73f, .93f, 1f), Shader.TileMode.CLAMP), 1f, .035f)
            canvas.restore()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        artwork?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
    }
}
