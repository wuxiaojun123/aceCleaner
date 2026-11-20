package com.nice.library_float.window.core

import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.view.doOnLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewbinding.ViewBinding
import com.nice.library_float.LogUtils
import com.nice.library_float.window.callback.FloatWindowCallback
import com.nice.library_float.window.model.FloatGravity
import com.nice.library_float.window.model.FloatMode
import com.nice.library_float.window.model.FloatWindowConfig
import com.nice.library_float.window.model.FloatWindowState
import com.nice.library_float.window.util.FloatUtil
import kotlin.math.abs

internal open class IFloatWindow(
    private val binding: ViewBinding,
    val config: FloatWindowConfig,
    private val onDestroyed: () -> Unit,
) {
    val contentView: View get() = binding.root
    private val manager = config.context.getSystemService(WindowManager::class.java)
    private var callback: FloatWindowCallback? = null
    private var edgeAnimator: ValueAnimator? = null
    var positionChanged: (() -> Unit)? = null
    var state = FloatWindowState.DETACHED
        private set
    val currentX: Int get() = params.x
    val currentY: Int get() = params.y
    private var downX = 0f
    private var downY = 0f
    private var initialX = 0
    private var initialY = 0
    private var dragging = false
    private val touchSlop = ViewConfiguration.get(config.context).scaledTouchSlop
    private val params = WindowManager.LayoutParams(
        config.width, config.height,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = config.startX
        y = config.startY
        if (config.fullScreen) {
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    }

    init {
        if (config.fullScreen) {
            val paddingLeft = contentView.paddingLeft
            val paddingTop = contentView.paddingTop
            val paddingRight = contentView.paddingRight
            val paddingBottom = contentView.paddingBottom
            if (Build.VERSION.SDK_INT < 30) {
                @Suppress("DEPRECATION")
                contentView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
            ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, insets ->
                val bars = insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
                view.setPadding(
                    paddingLeft + bars.left,
                    paddingTop + bars.top,
                    paddingRight + bars.right,
                    paddingBottom + bars.bottom,
                )
                insets
            }
        }
        contentView.setOnClickListener {
            runCatching { callback?.onClick() }
                .onFailure { LogUtils.w("Floating window action failed", "FloatWindow", it) }
        }
        if (config.mode == FloatMode.SLIDE) {
            contentView.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        edgeAnimator?.cancel()
                        downX = event.rawX
                        downY = event.rawY
                        initialX = params.x
                        initialY = params.y
                        dragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - downX
                        val deltaY = event.rawY - downY
                        if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) dragging = true
                        if (dragging) {
                            val bounds = FloatUtil.bounds(config.context)
                            updatePosition(
                                (initialX + deltaX.toInt()).coerceIn(0, (bounds.width() - view.width).coerceAtLeast(0)),
                                (initialY + deltaY.toInt()).coerceIn(0, (bounds.height() - view.height).coerceAtLeast(0)),
                            )
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) view.performClick()
                        if (dragging && config.autoEdge) snapToEdge()
                    }
                    MotionEvent.ACTION_CANCEL -> if (config.autoEdge) snapToEdge()
                }
                true
            }
        }
    }

    fun getBinding(): ViewBinding = binding
    fun setCallback(value: FloatWindowCallback) { callback = value }

    fun add(visible: Boolean) {
        check(state != FloatWindowState.DESTROYED)
        if (state != FloatWindowState.DETACHED) { setVisible(visible); return }
        check(Settings.canDrawOverlays(config.context)) { "Overlay permission is missing" }
        manager.addView(contentView, params)
        if (config.fullScreen) ViewCompat.requestApplyInsets(contentView)
        state = FloatWindowState.ATTACHED_VISIBLE
        contentView.doOnLayout {
            if (state == FloatWindowState.DESTROYED) return@doOnLayout
            if (config.fullScreen) {
                updatePosition(0, 0)
                return@doOnLayout
            }
            val bounds = FloatUtil.bounds(config.context)
            val x = when (config.initialGravity) {
                FloatGravity.TOP_RIGHT -> bounds.width() - it.width - config.startX
                else -> (bounds.width() - it.width) / 2
            }
            val y = when (config.initialGravity) {
                FloatGravity.TOP_RIGHT -> config.startY
                FloatGravity.CENTER -> (bounds.height() - it.height) / 2
                FloatGravity.BOTTOM_CENTER -> bounds.height() - it.height
            }
            updatePosition(x.coerceAtLeast(0), y.coerceAtLeast(0))
        }
        setVisible(visible)
    }

    fun setVisible(visible: Boolean) {
        if (state == FloatWindowState.DETACHED || state == FloatWindowState.DESTROYED) return
        edgeAnimator?.cancel()
        contentView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        state = if (visible) FloatWindowState.ATTACHED_VISIBLE else FloatWindowState.ATTACHED_HIDDEN
    }

    fun updatePosition(x: Int, y: Int) {
        params.x = x
        params.y = y
        if (contentView.isAttachedToWindow) runCatching { manager.updateViewLayout(contentView, params) }
            .onFailure { LogUtils.w("Unable to move floating window", "FloatWindow", it); destroy() }
        positionChanged?.invoke()
    }

    private fun snapToEdge() {
        val width = FloatUtil.bounds(config.context).width()
        val target = if (params.x + contentView.width / 2 < width / 2) config.edgePadding
            else width - contentView.width - config.edgePadding
        edgeAnimator = ValueAnimator.ofInt(params.x, target.coerceAtLeast(0)).apply {
            duration = config.edgeAnimDuration
            addUpdateListener { updatePosition(it.animatedValue as Int, params.y) }
            start()
        }
    }

    fun destroy() {
        if (state == FloatWindowState.DESTROYED) return
        edgeAnimator?.cancel()
        positionChanged = null
        if (contentView.isAttachedToWindow) runCatching { manager.removeViewImmediate(contentView) }
            .onFailure { LogUtils.w("Unable to remove floating window", "FloatWindow", it) }
        state = FloatWindowState.DESTROYED
        callback = null
        onDestroyed()
    }
}
