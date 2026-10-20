package com.nice.aceclean.ui.widget

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.nice.aceclean.R

/** Rounded permission button with a configurable fill and a continuously sweeping highlight. */
class PermissionActionButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private var actionColor = ContextCompat.getColor(context, R.color.color_FF6675FF)
    private var shineColor = Color.WHITE
    private var sweepDuration = DEFAULT_SWEEP_DURATION_MS
    private var animationEnabled = true
    private var sweepProgress = 0f
    private var sweepAnimator: ValueAnimator? = null
    private var scaleAnimator: ValueAnimator? = null

    private val nextScalePulse = Runnable { startScalePulse() }

    init {
        context.obtainStyledAttributes(attrs, R.styleable.PermissionActionButtonView, defStyleAttr, 0).apply {
            try {
                actionColor = getColor(R.styleable.PermissionActionButtonView_permissionActionColor, actionColor)
                shineColor = getColor(R.styleable.PermissionActionButtonView_permissionActionShineColor, shineColor)
                sweepDuration = getInt(
                    R.styleable.PermissionActionButtonView_permissionActionSweepDuration,
                    DEFAULT_SWEEP_DURATION_MS.toInt(),
                ).toLong().coerceAtLeast(MIN_SWEEP_DURATION_MS)
            } finally {
                recycle()
            }
        }
        background = ContextCompat.getDrawable(context, R.drawable.bg_feature_button)?.mutate()
        backgroundTintList = ColorStateList.valueOf(actionColor)
    }

    fun setActionColor(@ColorInt color: Int) {
        actionColor = color
        backgroundTintList = ColorStateList.valueOf(color)
    }

    fun setShineColor(@ColorInt color: Int) {
        shineColor = color
        invalidate()
    }

    fun setAnimationEnabled(enabled: Boolean) {
        if (animationEnabled == enabled) return
        animationEnabled = enabled
        if (enabled) startSweep() else stopSweep()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startSweep()
    }

    override fun onDetachedFromWindow() {
        stopSweep()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startSweep() else stopSweep()
    }

    override fun onDraw(canvas: Canvas) {
        drawShine(canvas)
        super.onDraw(canvas)
    }

    private fun startSweep() {
        if (!animationEnabled || !isAttachedToWindow || !isShown) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) return
        if (sweepAnimator?.isRunning != true) {
            sweepAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = sweepDuration
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    sweepProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        if (scaleAnimator?.isRunning != true) {
            removeCallbacks(nextScalePulse)
            postDelayed(nextScalePulse, INITIAL_SCALE_DELAY_MS)
        }
    }

    private fun stopSweep() {
        sweepAnimator?.cancel()
        sweepAnimator = null
        scaleAnimator?.removeAllListeners()
        scaleAnimator?.cancel()
        scaleAnimator = null
        removeCallbacks(nextScalePulse)
        scaleX = 1f
        scaleY = 1f
        sweepProgress = 0f
        invalidate()
    }

    private fun startScalePulse() {
        if (!animationEnabled || !isAttachedToWindow || !isShown) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) return
        scaleAnimator = ValueAnimator.ofFloat(1f, SCALE_PEAK).apply {
            duration = SCALE_PULSE_DURATION_MS
            repeatCount = 1
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                scaleX = scale
                scaleY = scale
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    scaleAnimator = null
                    if (animationEnabled && isAttachedToWindow && isShown) {
                        postDelayed(nextScalePulse, SCALE_PULSE_INTERVAL_MS)
                    }
                }
            })
            start()
        }
    }

    private fun drawShine(canvas: Canvas) {
        if (width == 0 || height == 0 || shineColor == Color.TRANSPARENT) return
        val bandWidth = width * SHINE_WIDTH_RATIO
        val centerX = (sweepProgress * SHINE_TRAVEL_RATIO - SHINE_START_OFFSET) * width
        val transparent = Color.argb(0, Color.red(shineColor), Color.green(shineColor), Color.blue(shineColor))
        val softShine = Color.argb(SOFT_SHINE_ALPHA, Color.red(shineColor), Color.green(shineColor), Color.blue(shineColor))
        val brightShine = Color.argb(BRIGHT_SHINE_ALPHA, Color.red(shineColor), Color.green(shineColor), Color.blue(shineColor))
        shinePaint.shader = LinearGradient(
            centerX - bandWidth,
            0f,
            centerX + bandWidth,
            0f,
            intArrayOf(transparent, softShine, brightShine, softShine, transparent),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            Shader.TileMode.CLAMP,
        )
        clipPath.reset()
        clipPath.addRoundRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            height / 2f,
            height / 2f,
            Path.Direction.CW,
        )
        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        canvas.rotate(SHINE_ANGLE_DEGREES, width / 2f, height / 2f)
        canvas.drawRect(centerX - bandWidth, -height.toFloat(), centerX + bandWidth, height * 2f, shinePaint)
        canvas.restoreToCount(checkpoint)
        shinePaint.shader = null
    }

    private companion object {
        const val DEFAULT_SWEEP_DURATION_MS = 1_600L
        const val MIN_SWEEP_DURATION_MS = 200L
        const val SHINE_WIDTH_RATIO = 0.28f
        const val SHINE_TRAVEL_RATIO = 1.65f
        const val SHINE_START_OFFSET = 0.32f
        const val SHINE_ANGLE_DEGREES = -18f
        const val SOFT_SHINE_ALPHA = 28
        const val BRIGHT_SHINE_ALPHA = 78
        const val INITIAL_SCALE_DELAY_MS = 800L
        const val SCALE_PULSE_DURATION_MS = 260L
        const val SCALE_PULSE_INTERVAL_MS = 1_880L
        const val SCALE_PEAK = 1.045f
    }
}
