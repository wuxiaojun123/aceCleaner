package com.nice.aceclean.ui.base

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Keeps full-screen artwork behind the bars while moving interactive content inside them. */
internal object SystemBarInsets {
    private const val BACKGROUND_TAG = "system_bar_background"
    private const val TOP_OVERLAY_TAG = "system_bar_top_overlay"

    fun apply(root: View) {
        val foreground = (root as? ViewGroup)?.takeIf {
            it.childCount > 1 && it.getChildAt(0).tag == BACKGROUND_TAG
        }?.let { group ->
            (1 until group.childCount).map { childIndex ->
                val child = group.getChildAt(childIndex)
                val margins = child.layoutParams as? ViewGroup.MarginLayoutParams
                Foreground(child, margins?.leftMargin ?: 0, margins?.topMargin ?: 0,
                    margins?.rightMargin ?: 0, margins?.bottomMargin ?: 0,
                    child.layoutParams.height, child.paddingTop)
            }
        }

        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (foreground == null) {
                view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            } else {
                foreground.forEach { item ->
                    val params = item.view.layoutParams as ViewGroup.MarginLayoutParams
                    if (item.view.tag == TOP_OVERLAY_TAG) {
                        // Let the toolbar background reach behind the status bar; inset only its content.
                        params.height = item.height + bars.top
                        params.setMargins(item.left + bars.left, item.top,
                            item.right + bars.right, item.bottom)
                        item.view.setPadding(item.view.paddingLeft, item.paddingTop + bars.top,
                            item.view.paddingRight, item.view.paddingBottom)
                    } else {
                        params.setMargins(item.left + bars.left, item.top + bars.top,
                            item.right + bars.right, item.bottom + bars.bottom)
                    }
                    item.view.layoutParams = params
                }
            }
            insets
        }
        if (root.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(root)
        } else {
            root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(view)
                }

                override fun onViewDetachedFromWindow(view: View) = Unit
            })
        }
    }

    private data class Foreground(
        val view: View,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val height: Int,
        val paddingTop: Int,
    )
}
