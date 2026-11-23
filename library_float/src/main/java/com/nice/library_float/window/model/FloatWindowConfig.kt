package com.nice.library_float.window.model

import android.content.Context
import android.view.WindowManager

enum class FloatMode { FIXED, SLIDE }
enum class FloatGravity { TOP_RIGHT, BOTTOM_CENTER, CENTER }
enum class FloatWindowState { DETACHED, ATTACHED_VISIBLE, ATTACHED_HIDDEN, DESTROYED }

class FloatWindowConfig private constructor(val context: Context) {
    var mode = FloatMode.FIXED
    var initialGravity = FloatGravity.TOP_RIGHT
    var width = WindowManager.LayoutParams.WRAP_CONTENT
    var height = WindowManager.LayoutParams.WRAP_CONTENT
    var startX = 0
    var startY = 0
    var fullScreen = false
    var autoEdge = false
    var edgePadding = 0
    var edgeAnimDuration = 250L

    class Builder(context: Context) {
        private val config = FloatWindowConfig(context.applicationContext)
        fun mode(value: FloatMode) = apply { config.mode = value }
        fun initialGravity(value: FloatGravity) = apply { config.initialGravity = value }
        fun width(value: Int) = apply { config.width = value }
        fun height(value: Int) = apply { config.height = value }
        fun startX(value: Int) = apply { config.startX = value }
        fun startY(value: Int) = apply { config.startY = value }
        fun fullScreen(value: Boolean) = apply { config.fullScreen = value }
        fun autoEdge(value: Boolean) = apply { config.autoEdge = value }
        fun edgePadding(value: Int) = apply { config.edgePadding = value }
        fun edgeAnimDuration(value: Long) = apply { config.edgeAnimDuration = value }
        fun build() = config
    }

    companion object { fun builder(context: Context) = Builder(context) }
}
