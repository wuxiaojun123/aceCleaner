package com.nice.aceclean.ui.widget

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min
import kotlin.math.abs

/**
 * 支持双指缩放、拖动、双击放大/还原的图片视图。
 * 放大状态下会请求父容器（如 ViewPager2）不要拦截横向手势，
 * 只有当图片已平移到水平边缘时才把滑动交还给 ViewPager2 翻页。
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val baseMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val tempValues = FloatArray(9)

    private var minScale = 1f
    private var midScale = 2f
    private var maxScale = 4f

    private var onSingleTap: (() -> Unit)? = null
    private var previousTouchX = 0f
    private var previousTouchY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val current = currentScale()
                var factor = detector.scaleFactor
                if (current * factor < minScale) factor = minScale / current
                if (current * factor > maxScale) factor = maxScale / current
                drawMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                fixTranslation()
                imageMatrix = drawMatrix
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                performClick()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val target = if (currentScale() > minScale * 1.1f) minScale else midScale
                zoomTo(target, e.x, e.y)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (scaleDetector.isInProgress || currentScale() <= minScale) return false
                drawMatrix.postTranslate(-distanceX, -distanceY)
                fixTranslation()
                imageMatrix = drawMatrix
                return true
            }
        },
    )

    init {
        scaleType = ScaleType.MATRIX
        setOnClickListener { onSingleTap?.invoke() }
    }

    fun setOnSingleTapListener(listener: () -> Unit) {
        onSingleTap = listener
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        // 同步计算适配矩阵，避免“先用旧矩阵画一帧再跳正”的闪烁；
        // 若此时还没测量到尺寸，则交给 onSizeChanged 处理。
        if (width > 0 && height > 0) resetToFit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetToFit()
    }

    private fun resetToFit() {
        val drawable = drawable ?: return
        if (width == 0 || height == 0) return
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0) return
        val scale = min(width / dw, height / dh)
        val dx = (width - dw * scale) / 2f
        val dy = (height - dh * scale) / 2f
        baseMatrix.reset()
        baseMatrix.postScale(scale, scale)
        baseMatrix.postTranslate(dx, dy)
        drawMatrix.set(baseMatrix)
        imageMatrix = drawMatrix
        minScale = 1f
        midScale = 2.5f
        maxScale = 5f
    }

    private fun currentScale(): Float {
        drawMatrix.getValues(tempValues)
        val baseScale = baseScaleX()
        val scaleX = tempValues[Matrix.MSCALE_X]
        return if (baseScale == 0f) 1f else scaleX / baseScale
    }

    private fun baseScaleX(): Float {
        val values = FloatArray(9)
        baseMatrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private fun displayRect(): RectF {
        val drawable = drawable ?: return RectF()
        val rect = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        drawMatrix.mapRect(rect)
        return rect
    }

    private fun fixTranslation() {
        val rect = displayRect()
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val dx = when {
            rect.width() <= viewW -> (viewW - rect.width()) / 2f - rect.left
            rect.left > 0 -> -rect.left
            rect.right < viewW -> viewW - rect.right
            else -> 0f
        }
        val dy = when {
            rect.height() <= viewH -> (viewH - rect.height()) / 2f - rect.top
            rect.top > 0 -> -rect.top
            rect.bottom < viewH -> viewH - rect.bottom
            else -> 0f
        }
        drawMatrix.postTranslate(dx, dy)
    }

    private fun zoomTo(targetScale: Float, focusX: Float, focusY: Float) {
        val current = currentScale()
        val factor = targetScale / current
        drawMatrix.postScale(factor, factor, focusX, focusY)
        fixTranslation()
        imageMatrix = drawMatrix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                previousTouchX = event.x
                previousTouchY = event.y
                // Keep the first move so a second finger can start a pinch before the pager intercepts.
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - previousTouchX
                val dy = event.y - previousTouchY
                val keepGesture = event.pointerCount > 1 || scaleDetector.isInProgress ||
                    (currentScale() > minScale && (abs(dy) > abs(dx) || canPanHorizontally(dx)))
                parent?.requestDisallowInterceptTouchEvent(keepGesture)
                previousTouchX = event.x
                previousTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        gestureDetector.onTouchEvent(event)
        return true
    }

    /** 图片被放大且尚未滑到水平边缘时返回 true，让自身消费横向拖动。 */
    private fun canPanHorizontally(fingerDeltaX: Float): Boolean {
        if (currentScale() <= minScale) return false
        val rect = displayRect()
        return rect.width() > width && when {
            fingerDeltaX > 0 -> rect.left < -1f
            fingerDeltaX < 0 -> rect.right > width + 1f
            else -> true
        }
    }
}
