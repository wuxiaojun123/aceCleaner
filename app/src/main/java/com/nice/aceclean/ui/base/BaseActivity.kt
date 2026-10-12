package com.nice.aceclean.ui.base

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nice.aceclean.R
import com.nice.aceclean.util.LocaleHelper

abstract class BaseActivity<B : ViewBinding>(
    private val inflateBinding: (LayoutInflater) -> B,
    @param:LayoutRes private val layoutResId: Int,
) : AppCompatActivity() {

    protected lateinit var binding: B
        private set

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
        binding = inflateBinding(layoutInflater)
        setActivityContent(binding, layoutResId)
        initViews()
        initData()
    }

    @get:ColorRes
    protected open val statusBarColorRes: Int = R.color.page_status_bar

    @get:ColorRes
    protected open val navigationBarColorRes: Int = R.color.white

    private fun applySystemBarInsets(root: View) {
        if (root.fitsSystemWindows) return
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
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

    /** Replaces this activity's screen while preserving the shared system-bar inset handling. */
    protected fun <T : ViewBinding> setActivityContent(contentBinding: T, @LayoutRes layoutResId: Int): T {
        setContentView(contentBinding.root)
        syncStatusBarBackground(contentBinding.root, layoutResId)
        applySystemBarInsets(contentBinding.root)
        return contentBinding
    }

    private fun syncStatusBarBackground(root: View, @LayoutRes layoutResId: Int) {
        val fallbackColor = ContextCompat.getColor(this, statusBarColorRes)
        val color = when (layoutResId) {
            R.layout.activity_allowed_apps,
            R.layout.activity_notification_cleaning,
            R.layout.fragment_notification_cleaner ->
                ContextCompat.getColor(this, R.color.notification_manager_screen_top)
            else -> (root?.background as? ColorDrawable)?.color ?: fallbackColor
        }
        // The content is inset below the status bar, so the decor draws this exposed area.
        window.decorView.setBackgroundColor(color)

        // On older Android versions the system paints the status bar itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            window.statusBarColor = color
        }
    }

    protected abstract fun initViews()

    protected open fun initData() = Unit
}
