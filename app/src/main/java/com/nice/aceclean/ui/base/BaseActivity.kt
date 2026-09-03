package com.nice.aceclean.ui.base

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity(@param:LayoutRes private val layoutResId: Int) : AppCompatActivity() {

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutResId)
        initViews()
        initData()
    }

    protected fun <T : View> view(id: Int): T = findViewById(id)

    protected abstract fun initViews()

    protected open fun initData() = Unit
}
