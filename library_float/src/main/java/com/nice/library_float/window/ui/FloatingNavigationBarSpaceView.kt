package com.nice.library_float.window.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class FloatingNavigationBarSpaceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val height = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (layoutParams.height != height) layoutParams = layoutParams.apply { this.height = height }
            insets
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }
}
