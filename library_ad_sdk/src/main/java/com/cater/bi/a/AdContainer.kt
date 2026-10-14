package com.cater.bi.a

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.ContentProvider
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup

/**
 *
 * created on 2025/8/24
 * @author holmes
 */
interface AdContainer {

    fun getActivity(): Activity?

    fun getParent(): ViewGroup?

}

class ActivityContainer(private val activity: Activity) : AdContainer {
    override fun getActivity(): Activity? = activity
    override fun getParent(): ViewGroup? = activity.findViewById<ViewGroup>(android.R.id.content)
}

class ContextActivityContainer(private val activityCtx: Context): AdContainer {
    override fun getActivity(): Activity? {
        var wrapper: Context? = activityCtx
        while (wrapper is ContextWrapper) {
            if (wrapper is Application
                || wrapper is Service
            ) {
                return null
            }
            if (wrapper is Activity) {
                return wrapper
            }
            wrapper = wrapper.baseContext
        }
        return null
    }

    override fun getParent(): ViewGroup? {
        return getActivity()?.findViewById<ViewGroup>(android.R.id.content)
    }
}

class ViewContainer(private val g: ViewGroup) : AdContainer {
    override fun getActivity(): Activity? {
        val context = g.context

        var wrapper: Context? = context
        while (wrapper is ContextWrapper) {
            if (wrapper is Application
                || wrapper is Service
            ) {
                return null
            }
            if (wrapper is Activity) {
                return wrapper
            }
            wrapper = wrapper.baseContext
        }
        return null
    }

    override fun getParent(): ViewGroup? = g
}