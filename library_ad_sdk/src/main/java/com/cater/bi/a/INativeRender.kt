package com.cater.bi.a

import android.content.Context
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Native ad render interface for custom templates
 * 
 * created on 2025/8/16
 * @author holmes
 */
interface INativeRender {

    fun getRoot(context: Context, parent: ViewGroup, nativeData: Any?): View

    fun getRootView(): View?

    fun getTitleView(): TextView?

    fun getDescView(): TextView?

    fun getActionView(): View?

    fun getCloseView(): View?

    fun getLogoView(): View?

    fun getIconView(): View?

    fun registerView(nativeData: Any?)

    fun getMediaContainer(): ViewGroup?

    fun getViewMaxSize(context: Context): Size

    // Yandex specific views - required for Yandex native ads
    fun getDomainView(): TextView?

    fun getWarningView(): TextView?

    fun getAdFromView(): TextView?
}
