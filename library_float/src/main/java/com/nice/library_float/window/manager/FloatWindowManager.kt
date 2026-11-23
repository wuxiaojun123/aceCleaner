package com.nice.library_float.window.manager

import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding
import com.nice.library_float.window.core.IFloatWindow
import com.nice.library_float.window.model.FloatWindowConfig

internal class FloatWindowManager private constructor() {
    private val windows = mutableMapOf<String, IFloatWindow>()
    fun getFloatWindow(tag: String): IFloatWindow? = windows[tag]
    fun createFloatWindow(tag: String, bindingInflater: (LayoutInflater) -> ViewBinding, config: FloatWindowConfig): IFloatWindow {
        windows.remove(tag)?.destroy()
        return IFloatWindow(bindingInflater(LayoutInflater.from(config.context)), config) { windows.remove(tag) }
            .also { windows[tag] = it }
    }
    companion object {
        private val instance = FloatWindowManager()
        fun getInstance() = instance
    }
}
