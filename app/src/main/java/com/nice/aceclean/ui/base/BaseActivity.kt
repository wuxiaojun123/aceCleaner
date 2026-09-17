package com.nice.aceclean.ui.base

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nice.aceclean.R
import com.nice.aceclean.util.LocaleHelper

abstract class BaseActivity(@param:LayoutRes private val layoutResId: Int) : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    final override fun onCreate(savedInstanceState: Bundle?) {
        val statusBarColor = ContextCompat.getColor(this, statusBarColorRes)
        val navigationBarColor = ContextCompat.getColor(this, navigationBarColorRes)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(statusBarColor, statusBarColor),
            navigationBarStyle = SystemBarStyle.light(navigationBarColor, navigationBarColor),
        )
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(statusBarColor)
        setContentView(layoutResId)
        applySystemBarInsets(findViewById(android.R.id.content))
        initViews()
        initData()
    }

    @get:ColorRes
    protected open val statusBarColorRes: Int = R.color.page_status_bar

    @get:ColorRes
    protected open val navigationBarColorRes: Int = R.color.white

    private fun applySystemBarInsets(content: View) {
        val layoutRoot = (content as? ViewGroup)?.getChildAt(0)
        if (layoutRoot?.fitsSystemWindows == true) return
        val initialLeft = content.paddingLeft
        val initialTop = content.paddingTop
        val initialRight = content.paddingRight
        val initialBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom,
            )
            windowInsets
        }
    }

    protected fun <T : View> view(id: Int): T = findViewById(id)

    /** Replaces this activity's screen while preserving the shared system-bar inset handling. */
    protected fun setActivityContent(@LayoutRes layoutResId: Int) {
        setContentView(layoutResId)
        applySystemBarInsets(findViewById(android.R.id.content))
    }

    protected abstract fun initViews()

    protected open fun initData() = Unit
}
