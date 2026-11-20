package com.nice.aceclean.ifloat

import android.content.Context
import com.cater.bi.e.Eve
import com.nice.aceclean.ad.HotStartManager
import com.nice.aceclean.notification.NotificationInterceptStore
import com.nice.aceclean.ui.main.ScanFeature
import com.nice.aceclean.ui.main.SplashActivity
import com.nice.aceclean.util.LogUtils
import com.nice.library_float.FloatRoute
import com.nice.library_float.FloatingBallHost

internal class AppFloatingBallHost : FloatingBallHost {
    override fun interceptedToday(context: Context): Int = NotificationInterceptStore.interceptedToday(context)

    override fun onBeforeExternalEntry() = HotStartManager.markSkipNextHotStart()

    override fun openRoute(context: Context, route: FloatRoute) {
        val feature = when (route) {
            FloatRoute.QUICK_SCAN -> ScanFeature.CLEAN_UP
            FloatRoute.PHOTOS -> ScanFeature.DUPLICATE_PHOTO
            FloatRoute.NOTIFY -> ScanFeature.NOTIFICATIONS
            FloatRoute.WIDGET_PROCESS_MANAGER -> ScanFeature.RAM
        }
        context.startActivity(SplashActivity.floatingIntent(context, feature))
    }

    override fun reportEvent(event: String, params: Map<String, String>) {
        runCatching { Eve.send(event, params) }
            .onFailure { LogUtils.w("Unable to report floating-ball event", "FloatingBall", it) }
    }

    override fun log(level: String, tag: String, message: String, error: Throwable?) {
        when (level) {
            "error" -> LogUtils.e(message, tag, error)
            "warning" -> LogUtils.w(message, tag, error)
            else -> LogUtils.d(message, tag)
        }
    }
}
