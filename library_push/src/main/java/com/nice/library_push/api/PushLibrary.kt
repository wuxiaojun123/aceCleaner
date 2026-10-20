package com.nice.library_push.api

import android.app.Application
import android.content.Context
import com.nice.library_push.backend.PushConfigSource
import com.nice.library_push.core.PushRuntime
import com.nice.library_push.config.PushConfig
import kotlinx.coroutines.flow.StateFlow

/** Public entry point. A backend, routes and optional business templates are injected by the host. */
object PushLibrary {
    @Volatile private var runtime: PushRuntime? = null

    @Synchronized
    fun initialize(
        application: Application,
        source: PushConfigSource,
        router: PushRouter = PushRouter { context, click ->
            if (click.type == "news" && isHttpsUrl(click.target)) {
                com.nice.library_push.business.news.PushNewsActivity.open(context, click.target)
            } else PushRuntime.openAppFallback(context)
        },
        tracker: PushTracker = PushTracker { _, _ -> },
        templates: List<PushTemplate> = emptyList(),
        isVip: () -> Boolean = { false },
        isOrganic: () -> Boolean = { false },
        onToken: (String) -> Unit = {},
        topic: String? = null,
    ) {
        if (runtime != null) return
        runtime = PushRuntime(application, source, router, tracker, templates, isVip, isOrganic, onToken, topic).also { it.start() }
    }

    fun refresh() { runtime?.refresh() }
    /** Call after the host returns from notification, media or overlay permission settings. */
    fun refreshPermissions() { runtime?.refreshPermissions() }
    fun trigger(event: PushEvent) { runtime?.trigger(event) }
    fun triggerAppScene(subType: Int = 0) { trigger(PushEvent(com.nice.library_push.config.PushScene.APP, subType)) }
    /** Content is cached locally; refreshing the scene config need not fetch it again. */
    fun setWeather(data: PushWeatherData?) { runtime?.setWeather(data) }
    fun setNews(items: List<PushNewsItem>) { runtime?.setNews(items) }
    fun triggerCalendar(scene: String = com.nice.library_push.config.PushScene.APP) {
        trigger(PushEvent(scene, templateType = "calendar"))
    }
    fun triggerWeather(scene: String = com.nice.library_push.config.PushScene.APP) {
        trigger(PushEvent(scene, templateType = "weather"))
    }
    fun triggerNews(scene: String = com.nice.library_push.config.PushScene.APP) {
        trigger(PushEvent(scene, templateType = "news"))
    }
    fun showNotification() { trigger(PushEvent(com.nice.library_push.config.PushScene.UNLOCK)) }
    fun currentConfig(): PushConfig? = runtime?.config?.value
    fun configFlow(): StateFlow<PushConfig>? = runtime?.config
    fun onNewFirebaseToken(token: String) { runtime?.onNewToken(token) }
    fun onFirebaseData(data: Map<String, String>) { runtime?.onFirebaseData(data) }

    internal fun handleClick(context: Context, click: PushClick, notificationId: Int) {
        runtime?.handleClick(context, click, notificationId)
            ?: run {
                if (click.action == "close" || click.autoCancel) {
                    (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(notificationId)
                }
                if (click.action != "close") {
                    if (click.type == "news" && isHttpsUrl(click.target)) {
                        com.nice.library_push.business.news.PushNewsActivity.open(context, click.target)
                    } else PushRuntime.openAppFallback(context)
                }
            }
    }

    internal fun timer() { runtime?.trigger(PushEvent(com.nice.library_push.config.PushScene.TIMER)) }

    @Synchronized fun destroy() {
        runtime?.stop()
        runtime = null
    }
}
