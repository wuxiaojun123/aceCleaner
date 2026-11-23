package com.nice.library_float.window.util

import android.content.Context
import android.os.Build
import android.view.WindowManager

object FloatUtil {
    fun getScreenHeight(context: Context): Int = bounds(context).height()
    fun getRealScreenHeight(context: Context): Int = getScreenHeight(context)
    fun bounds(context: Context): android.graphics.Rect {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= 30) return manager.currentWindowMetrics.bounds
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        manager.defaultDisplay.getRealMetrics(metrics)
        return android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }
}
