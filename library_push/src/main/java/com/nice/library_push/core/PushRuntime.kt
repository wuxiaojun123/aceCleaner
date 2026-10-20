package com.nice.library_push.core

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.nice.library_push.api.*
import com.nice.library_push.backend.PushConfigSource
import com.nice.library_push.business.PushTemplates
import com.nice.library_push.business.PushBusinessStore
import com.nice.library_push.config.PushConfig
import com.nice.library_push.config.PushConfigParser
import com.nice.library_push.display.PushPresenter
import com.nice.library_push.display.PushImageLoader
import com.google.firebase.messaging.FirebaseMessaging
import com.nice.library_push.triggers.PushTriggerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import kotlin.random.Random

internal class PushRuntime(
    private val application: Application,
    private val source: PushConfigSource,
    private val router: PushRouter,
    private val tracker: PushTracker,
    extraTemplates: List<PushTemplate>,
    private val isVip: () -> Boolean,
    private val isOrganic: () -> Boolean,
    private val tokenCallback: (String) -> Unit,
    private val topic: String?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val presenter = PushPresenter(application)
    private val triggers = PushTriggerRegistry(application, ::trigger)
    private val business = PushBusinessStore(application)
    private val templates = (PushTemplates.defaults(business::weather, business::nextNews, business::recordNewsShown) + extraTemplates)
        .associateBy(PushTemplate::type)
    private val displayMutex = Mutex()
    private val lock = Any()
    val config = MutableStateFlow(PushConfig())

    fun start() {
        prefs.getString(CONFIG_KEY, null)?.let { raw ->
            runCatching { PushConfigParser.parse(raw) }.getOrNull()?.let { applyConfig(it) }
        }
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener(::onNewToken)
            topic?.takeIf { it.isNotBlank() }?.let { FirebaseMessaging.getInstance().subscribeToTopic(it) }
        }
        refresh()
    }

    fun refresh() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { source.fetch() } }
                .onSuccess { raw ->
                    runCatching { PushConfigParser.parse(raw) }
                        .onSuccess { parsed ->
                            prefs.edit().putString(CONFIG_KEY, raw).apply()
                            applyConfig(parsed)
                            track("config_updated")
                        }.onFailure { track("config_error", mapOf("reason" to (it.message ?: "parse"))) }
                }.onFailure { track("config_error", mapOf("reason" to (it.message ?: "fetch"))) }
        }
    }

    fun refreshPermissions() { triggers.update(config.value) }

    private fun applyConfig(next: PushConfig) {
        if (next.hasWeather) business.setWeather(next.weather)
        next.news?.let(business::setNews)
        config.value = next
        triggers.update(next)
    }

    fun trigger(event: PushEvent) {
        scope.launch { displayMutex.withLock { show(event) } }
    }

    fun setWeather(value: PushWeatherData?) { business.setWeather(value) }
    fun setNews(items: List<PushNewsItem>) { business.setNews(items) }

    private suspend fun show(event: PushEvent) {
        val scene = config.value.scene(event.scene)
        fun skip(reason: String) = track("notification_skipped", mapOf("scene" to event.scene, "reason" to reason))
        if (!scene.enabled) { skip("scene_disabled"); return }
        if (scene.excludeVip && isVip()) { skip("vip_user"); return }
        if (isOrganic() && scene.organicDelaySeconds < 0) { skip("organic_disabled"); return }
        val now = System.currentTimeMillis()
        val installed = runCatching { application.packageManager.getPackageInfo(application.packageName, 0).firstInstallTime }.getOrDefault(now)
        if (now - installed < scene.installDelaySeconds * 1000) { skip("install_delay"); return }
        if (isOrganic() && now - installed < scene.organicDelaySeconds * 1000) { skip("organic_delay"); return }
        val power = application.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!power.isInteractive) { skip("screen_off"); return }
        if (triggers.isForeground) { skip("app_foreground"); return }
        if (!presenter.canDisplay(scene.displayMode)) { skip("display_unavailable"); return }
        val effectiveCooldown = scene.effectiveCooldownSeconds()
        if (effectiveCooldown < 0) { skip("time_window_disabled"); return }
        val pool = scene.templates.filter { it.weight > 0 && (event.templateType.isBlank() || it.type == event.templateType) }
        val exact = pool.filter { it.subType == event.subType }
        val matched = exact.ifEmpty { if (event.subType == 0) pool else pool.filter { it.subType == 0 } }
        val candidates = matched
            .mapNotNull { spec -> templates[spec.type]?.takeIf { it.isAvailable(event, spec) }?.let { spec to it } }
        if (candidates.isEmpty()) { skip("no_template"); return }
        val chosen = weightedChoice(candidates)
        val content = runCatching { chosen.second.render(application, event, chosen.first) }.getOrElse {
            skip("render_error"); return
        }
        if (content.title.isBlank() && content.body.isBlank()) { skip("empty_content"); return }
        val id: Int
        var previousSceneTime = 0L
        var previousGlobalTime = 0L
        val today = Calendar.getInstance().let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
        val sceneCountKey = "count_${today}_${event.scene}"
        val totalCountKey = "total_$today"
        synchronized(lock) {
            if (prefs.getInt("counter_day", -1) != today) {
                val editor = prefs.edit().putInt("counter_day", today)
                prefs.all.keys.filter { it.startsWith("count_") || it.startsWith("total_") }.forEach(editor::remove)
                editor.apply()
            }
            val sceneCount = prefs.getInt(sceneCountKey, 0)
            val totalCount = prefs.getInt(totalCountKey, 0)
            val lastScene = prefs.getLong("last_${event.scene}", 0)
            val lastGlobal = prefs.getLong("last_global", 0)
            val reason = when {
                scene.dailyLimit > 0 && sceneCount >= scene.dailyLimit -> "daily_cap"
                config.value.globalDailyLimit > 0 && totalCount >= config.value.globalDailyLimit -> "global_daily_cap"
                lastScene > 0 && now - lastScene < effectiveCooldown * 1000 -> "cooldown"
                lastGlobal > 0 && now - lastGlobal < config.value.globalIntervalMillis -> "global_interval"
                else -> null
            }
            if (reason != null) { skip(reason); return }
            previousSceneTime = lastScene
            previousGlobalTime = lastGlobal
            id = (prefs.getInt("next_id", 3100) + 1).let { if (it > 999_999) 3101 else it }
            prefs.edit().putInt(sceneCountKey, sceneCount + 1)
                .putInt(totalCountKey, totalCount + 1).putLong("last_${event.scene}", now)
                .putLong("last_global", now).putInt("next_id", id).apply()
        }
        val click = PushClick(event.scene, chosen.first.tempId, chosen.first.contentId, "content", content.target,
            chosen.first.type, chosen.first.autoCancel)
        val shown = runCatching {
            presenter.show(id, content, chosen.first, scene.displayMode, config.value.channelName, click) {
                !triggers.isForeground && power.isInteractive && config.value.scene(event.scene) == scene &&
                    (content.expiresAtMillis == 0L || content.expiresAtMillis > System.currentTimeMillis())
            }
        }.getOrDefault(false)
        if (!shown) synchronized(lock) {
            if (prefs.getInt("next_id", 0) == id) {
                prefs.edit().putInt(sceneCountKey, (prefs.getInt(sceneCountKey, 1) - 1).coerceAtLeast(0))
                    .putInt(totalCountKey, (prefs.getInt(totalCountKey, 1) - 1).coerceAtLeast(0))
                    .putLong("last_${event.scene}", previousSceneTime)
                    .putLong("last_global", previousGlobalTime).apply()
            }
        }
        if (shown) {
            chosen.second.onShown(content)
            if (content.style == PushStyle.NEWS) {
                business.nextNews()?.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    scope.launch { PushImageLoader.load(url) }
                }
            }
        }
        track(if (shown) "notification_shown" else "notification_skipped", mapOf("scene" to event.scene, "tempId" to chosen.first.tempId.toString(), "reason" to if (shown) "" else "display_unavailable"))
    }

    private fun weightedChoice(items: List<Pair<com.nice.library_push.config.PushTemplateSpec, PushTemplate>>): Pair<com.nice.library_push.config.PushTemplateSpec, PushTemplate> {
        val sum = items.sumOf { it.first.weight.toLong() }
        var pick = Random.nextLong(sum)
        for (item in items) {
            pick -= item.first.weight
            if (pick < 0) return item
        }
        return items.last()
    }

    fun handleClick(context: Context, click: PushClick, notificationId: Int) {
        if (click.action == "close" || click.autoCancel) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
        }
        presenter.dismissOverlay()
        track("notification_clicked", mapOf("scene" to click.scene, "action" to click.action, "tempId" to click.tempId.toString()))
        if (click.action != "close") runCatching { router.open(context, click) }.onFailure { openAppFallback(context) }
    }

    fun onNewToken(token: String) {
        runCatching { tokenCallback(token) }
        track("fcm_token", mapOf("length" to token.length.toString()))
    }

    fun onFirebaseData(data: Map<String, String>) {
        track("fcm_message", mapOf("keys" to data.keys.joinToString(",")))
        if (data["type"] == "refresh_config") refresh()
        // Direct campaign display is deliberately not inferred from the reference AAR.
    }

    fun stop() {
        triggers.stop()
        presenter.stop()
        scope.cancel()
    }

    private fun track(event: String, values: Map<String, String> = emptyMap()) {
        runCatching { tracker.track(event, values) }
    }

    companion object {
        private const val PREFS = "nice_push_library"
        private const val CONFIG_KEY = "last_good_config"

        fun openAppFallback(context: Context) {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(it)
            }
        }

    }
}
