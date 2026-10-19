package com.nice.aceclean.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import com.nice.aceclean.R
import kotlin.math.ceil
import kotlin.math.sin

/**
 * Draws and animates only the background drawable. Text is drawn afterwards
 * by TextView, so its position and hit area remain unchanged.
 */
class WobbleBackgroundTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var wobbleDrawable: Drawable? = null
    private var wobbleAnimator: ValueAnimator? = null
    private var phase = 0f
    private var amplitudePx = 6f * resources.displayMetrics.density
    private var skewAmount = 0.08f
    private var durationMs = 1800L
    private var pauseDurationMs = 0L
    private var wobbleEnabled = true

    init {
        val values = context.obtainStyledAttributes(
            attrs, R.styleable.WobbleBackgroundTextView, defStyleAttr, 0
        )
        try {
            amplitudePx = values.getDimension(
                R.styleable.WobbleBackgroundTextView_wobbleAmplitude, amplitudePx
            ).coerceAtLeast(0f)
            skewAmount = values.getFloat(
                R.styleable.WobbleBackgroundTextView_wobbleSkew, skewAmount
            ).coerceIn(0f, 0.25f)
            durationMs = values.getInt(
                R.styleable.WobbleBackgroundTextView_wobbleDuration, durationMs.toInt()
            ).coerceAtLeast(200).toLong()
            pauseDurationMs = values.getInt(
                R.styleable.WobbleBackgroundTextView_wobblePauseDuration, 0
            ).coerceAtLeast(0).toLong()
            wobbleEnabled = values.getBoolean(
                R.styleable.WobbleBackgroundTextView_wobbleEnabled, true
            )
            val supplied = values.getDrawable(R.styleable.WobbleBackgroundTextView_wobbleDrawable)
            setWobbleDrawable(supplied ?: background)
        } finally {
            values.recycle()
        }
        // The ordinary View background would be drawn outside onDraw and move with neither
        // the custom canvas nor the text. We draw it ourselves before super.onDraw instead.
        background = null
    }

    fun setWobbleDrawable(drawable: Drawable?) {
        wobbleDrawable?.callback = null
        wobbleDrawable = drawable?.mutate()?.also {
            it.callback = this
            it.state = drawableState
        }
        if (wobbleDrawable == null) stopWobble() else startWobble()
        invalidate()
    }

    fun setWobbleEnabled(enabled: Boolean) {
        if (wobbleEnabled == enabled) return
        wobbleEnabled = enabled
        if (enabled) startWobble() else stopWobble()
    }

    /** Time at the center position between complete wobble cycles, in milliseconds. */
    fun setWobblePauseDuration(pauseMillis: Long) {
        val newDuration = pauseMillis.coerceIn(0L, Int.MAX_VALUE.toLong())
        if (pauseDurationMs == newDuration) return
        pauseDurationMs = newDuration
        stopWobble()
        startWobble()
    }

    fun startWobble() {
        if (!wobbleEnabled || !isAttachedToWindow || !isShown || wobbleDrawable == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) {
            phase = 0f
            invalidate()
            return
        }
        if (wobbleAnimator?.isRunning == true) return
        val cycleDurationMs = durationMs + pauseDurationMs
        wobbleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = cycleDurationMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val elapsedMs = it.animatedFraction.toDouble() * cycleDurationMs.toDouble()
                val newPhase = if (elapsedMs < durationMs.toDouble()) {
                    (elapsedMs / durationMs.toDouble() * 2.0 * Math.PI).toFloat()
                } else {
                    0f
                }
                if (phase != newPhase) {
                    phase = newPhase
                    invalidate()
                }
            }
            start()
        }
    }

    fun stopWobble() {
        wobbleAnimator?.cancel()
        wobbleAnimator = null
        phase = 0f
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startWobble()
    }

    override fun onDetachedFromWindow() {
        stopWobble()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) startWobble() else stopWobble()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        wobbleDrawable?.let {
            if (it.isStateful) it.state = drawableState
        }
    }

    override fun verifyDrawable(who: Drawable): Boolean =
        who === wobbleDrawable || super.verifyDrawable(who)

    override fun onDraw(canvas: Canvas) {
        wobbleDrawable?.let { drawable ->
            val wave = sin(phase)
            val inset = ceil(amplitudePx + height * skewAmount / 2f).toInt()
                .coerceAtMost(width / 4)
            // Keep antialiased rounded corners inside the View's clip. Drawing exactly
            // to y = height can leave a one-pixel fragment at the lower corners.
            val verticalInset = ceil(resources.displayMetrics.density).toInt()
                .coerceAtMost(height / 4)
            drawable.setBounds(inset, verticalInset, width - inset, height - verticalInset)
            val checkpoint = canvas.save()
            canvas.translate(amplitudePx * wave, 0f)
            canvas.translate(width / 2f, height / 2f)
            canvas.skew(skewAmount * wave, 0f)
            canvas.translate(-width / 2f, -height / 2f)
            drawable.draw(canvas)
            canvas.restoreToCount(checkpoint)
        }
        super.onDraw(canvas)
    }
}
