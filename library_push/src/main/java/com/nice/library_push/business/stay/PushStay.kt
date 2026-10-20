package com.nice.library_push.business.stay

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Optional ongoing notification. The host explicitly starts and stops it. */
object PushStay {
    fun start(context: Context, title: String, text: String): Boolean {
        val intent = Intent(context, PushStayService::class.java).apply {
            putExtra(PushStayService.EXTRA_TITLE, title)
            putExtra(PushStayService.EXTRA_TEXT, text)
        }
        return runCatching { ContextCompat.startForegroundService(context, intent); true }.getOrDefault(false)
    }

    fun stop(context: Context) { context.stopService(Intent(context, PushStayService::class.java)) }
}
