package com.nice.aceclean.ui.widget

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.PathInterpolator
import android.widget.Checkable
import android.widget.Switch
import androidx.core.content.ContextCompat
import com.nice.aceclean.R
import kotlin.math.min

class IosSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), Checkable {

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ios_switch_thumb)
        setShadowLayer(dp(1.5f), 0f, dp(1f), ContextCompat.getColor(context, R.color.ios_switch_thumb_shadow))
    }
    private val trackBounds = RectF()
    private val offColor = ContextCompat.getColor(context, R.color.ios_switch_track_off)
    private val onColor = ContextCompat.getColor(context, R.color.ios_switch_track_on)
    private val colorEvaluator = ArgbEvaluator()
    private var checked = false
    private var thumbProgress = 0f
    private var animator: ValueAnimator? = null
    private var checkedChangeListener: ((IosSwitchView, Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun isChecked(): Boolean = checked

    override fun setChecked(value: Boolean) {
        if (checked == value) return
        checked = value
        animateThumb(if (value) 1f else 0f)
        checkedChangeListener?.invoke(this, value)
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    override fun toggle() {
        isChecked = !checked
    }

    fun setOnCheckedChangeListener(listener: ((IosSwitchView, Boolean) -> Unit)?) {
        checkedChangeListener = listener
    }

    override fun performClick(): Boolean {
        val handled = super.performClick()
        toggle()
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        return handled
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(DESIRED_WIDTH_DP).toInt() + paddingLeft + paddingRight
        val desiredHeight = dp(TOUCH_HEIGHT_DP).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val availableHeight = (height - paddingTop - paddingBottom).toFloat()
        val trackWidth = min(dp(DESIRED_WIDTH_DP), availableWidth)
        val trackHeight = min(dp(TRACK_HEIGHT_DP), availableHeight)
        val left = paddingLeft + (availableWidth - trackWidth) / 2f
        val top = paddingTop + (availableHeight - trackHeight) / 2f
        trackBounds.set(left, top, left + trackWidth, top + trackHeight)

        trackPaint.color = colorEvaluator.evaluate(thumbProgress, offColor, onColor) as Int
        canvas.drawRoundRect(trackBounds, trackHeight / 2f, trackHeight / 2f, trackPaint)

        val thumbRadius = (trackHeight - dp(THUMB_INSET_DP) * 2f) / 2f
        val startX = trackBounds.left + dp(THUMB_INSET_DP) + thumbRadius
        val endX = trackBounds.right - dp(THUMB_INSET_DP) - thumbRadius
        val visualProgress = if (layoutDirection == LAYOUT_DIRECTION_RTL) 1f - thumbProgress else thumbProgress
        val centerX = startX + (endX - startX) * visualProgress
        canvas.drawCircle(centerX, trackBounds.centerY(), thumbRadius, thumbPaint)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        alpha = if (isEnabled) 1f else DISABLED_ALPHA
        invalidate()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Switch::class.java.name
        info.isCheckable = true
        info.isChecked = checked
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun animateThumb(target: Float) {
        animator?.cancel()
        if (!isLaidOut) {
            thumbProgress = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(thumbProgress, target).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = SWITCH_INTERPOLATOR
            addUpdateListener {
                thumbProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun dp(value: Float): Float = value * density

    private companion object {
        const val DESIRED_WIDTH_DP = 51f
        const val TOUCH_HEIGHT_DP = 48f
        const val TRACK_HEIGHT_DP = 31f
        const val THUMB_INSET_DP = 2f
        const val DISABLED_ALPHA = 0.45f
        const val ANIMATION_DURATION_MS = 180L
        val SWITCH_INTERPOLATOR = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    }
}
