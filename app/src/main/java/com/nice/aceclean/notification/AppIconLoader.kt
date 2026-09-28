package com.nice.aceclean.notification

import android.content.Context
import android.graphics.drawable.Drawable

object AppIconLoader {
    fun load(context: Context, packageName: String): Drawable? = runCatching {
        context.packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun loadLabel(context: Context, packageName: String): String? = runCatching {
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(applicationInfo).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() && it != packageName }
}
