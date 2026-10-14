package com.nice.library_ad_sdk.render

import android.content.Context
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.cater.bi.a.INativeRender
import com.nice.library_ad_sdk.R

/** Native-ad renderer shared by home, recent, tools, and exit placements. */
class AppNativeAdRender : INativeRender {

    private var root: View? = null
    private var title: TextView? = null
    private var description: TextView? = null
    private var action: View? = null
    private var icon: View? = null
    private var media: ViewGroup? = null

    override fun getRoot(context: Context, parent: ViewGroup, nativeData: Any?): View {
        return root ?: LayoutInflater.from(context)
            .inflate(R.layout.layout_native_ad_compact, parent, false)
            .also { view ->
                root = view
                title = view.findViewById(R.id.nativeAdTitle)
                description = view.findViewById(R.id.nativeAdDescription)
                action = view.findViewById(R.id.nativeAdAction)
                icon = view.findViewById(R.id.nativeAdIcon)
                media = view.findViewById(R.id.nativeAdMedia)
            }
    }

    override fun getRootView(): View? = root
    override fun getTitleView(): TextView? = title
    override fun getDescView(): TextView? = description
    override fun getActionView(): View? = action
    override fun getCloseView(): View? = null
    override fun getLogoView(): View? = null
    override fun getIconView(): View? = icon
    override fun registerView(nativeData: Any?) = Unit
    override fun getMediaContainer(): ViewGroup? = media

    override fun getViewMaxSize(context: Context): Size {
        val metrics = context.resources.displayMetrics
        return Size(metrics.widthPixels, (132 * metrics.density).toInt())
    }

    override fun getDomainView(): TextView? = null
    override fun getWarningView(): TextView? = null
    override fun getAdFromView(): TextView? = null
}