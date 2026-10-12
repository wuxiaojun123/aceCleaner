package com.nice.aceclean.ui.base

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import androidx.core.content.ContextCompat
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
        val navigationBarColor = ContextCompat.getColor(this, navigationBarColorRes)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(navigationBarColor, navigationBarColor),
        )
        super.onCreate(savedInstanceState)
        binding = inflateBinding(layoutInflater)
        setActivityContent(binding, layoutResId)
        initViews()
        initData()
    }

    @get:ColorRes
    protected open val navigationBarColorRes: Int = R.color.white

    /** Replaces this activity's screen while preserving the shared system-bar inset handling. */
    protected fun <T : ViewBinding> setActivityContent(contentBinding: T, @LayoutRes layoutResId: Int): T {
        setContentView(contentBinding.root)
        // MainActivity hosts a fragment whose background and content manage their own insets.
        if (layoutResId != R.layout.activity_main) SystemBarInsets.apply(contentBinding.root)
        return contentBinding
    }

    protected abstract fun initViews()

    protected open fun initData() = Unit
}
