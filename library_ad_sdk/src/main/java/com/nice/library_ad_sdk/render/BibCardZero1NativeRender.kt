package com.nice.library_ad_sdk.render

import android.content.Context
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.cater.bi.a.INativeRender
import com.nice.library_ad_sdk.R

/**
 * Native-ad renderer for [com.nice.library_ad_sdk.R.layout.bib_layout_card_zero1].
 *
 * Create a new renderer for each native-ad container so its view references are
 * never shared between two simultaneously displayed ads.
 */
class BibCardZero1NativeRender : INativeRender {

    private var root: View? = null
    private var title: TextView? = null
    private var description: TextView? = null
    private var action: View? = null
    private var close: View? = null
    private var icon: View? = null
    private var media: ViewGroup? = null

    override fun getRoot(context: Context, parent: ViewGroup, nativeData: Any?): View {
        return LayoutInflater.from(context)
            .inflate(R.layout.bib_layout_card_zero1, parent, false)
            .also { view ->
                root = view
                title = view.findViewById(R.id.title)
                description = view.findViewById(R.id.description)
                action = view.findViewById(R.id.action)
                close = view.findViewById(R.id.close)
                icon = view.findViewById(R.id.icon)
                media = view.findViewById(R.id.media_container)
            }
    }

    override fun getRootView(): View? = root
    override fun getTitleView(): TextView? = title
    override fun getDescView(): TextView? = description
    override fun getActionView(): View? = action
    override fun getCloseView(): View? = close
    override fun getLogoView(): View? = null
    override fun getIconView(): View? = icon
    override fun registerView(nativeData: Any?) = Unit
    override fun getMediaContainer(): ViewGroup? = media

    override fun getViewMaxSize(context: Context): Size {
        val metrics = context.resources.displayMetrics
        return Size(metrics.widthPixels, (CARD_HEIGHT_DP * metrics.density).toInt())
    }

    override fun getDomainView(): TextView? = null
    override fun getWarningView(): TextView? = null
    override fun getAdFromView(): TextView? = null

    private companion object {
        const val CARD_HEIGHT_DP = 158
    }
}