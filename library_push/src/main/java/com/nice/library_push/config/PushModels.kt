package com.nice.library_push.config

import com.nice.library_push.api.PushNewsItem
import com.nice.library_push.api.PushWeatherData

/** Scene keys match the reference library's remote configuration. */
object PushScene {
    const val UNLOCK = "ul"
    const val TIMER = "ta"
    const val BACKGROUND = "ab"
    const val PACKAGE = "pkg"
    const val WIFI = "wifi"
    const val SCREENSHOT = "rq"
    const val BATTERY = "dc"
    const val APP = "app"
    const val FILE = "file"
    val ALL = setOf(UNLOCK, TIMER, BACKGROUND, PACKAGE, WIFI, SCREENSHOT, BATTERY, APP, FILE)
}

enum class PushDisplayMode { OVERLAY_PREFERRED, NOTIFICATION, OVERLAY_ONLY }

data class PushTemplateSpec(
    val tempId: Int,
    val type: String = "default",
    val weight: Int = 1,
    val subType: Int = 0,
    val contentId: Int = 0,
    val title: String = "",
    val content: String = "",
    val buttonText: String = "",
    val imageUrls: List<String> = emptyList(),
    val target: String = "",
    val autoCancel: Boolean = true,
    val ongoing: Boolean = false,
    val showClose: Boolean = true,
    val showOverlayClose: Boolean = true,
)

data class PushSceneConfig(
    val enabled: Boolean = false,
    val cooldownSeconds: Long = 0,
    /** HH:mm-HH:mm:seconds segments separated by semicolons; -1 disables the segment. */
    val cooldownByTime: String = "",
    val installDelaySeconds: Long = 0,
    val organicDelaySeconds: Long = 0,
    val dailyLimit: Int = 0,
    val excludeVip: Boolean = false,
    val displayMode: PushDisplayMode = PushDisplayMode.NOTIFICATION,
    val batteryLevels: List<Int> = emptyList(),
    val fileSuffixes: List<String> = emptyList(),
    val templates: List<PushTemplateSpec> = emptyList(),
) {
    fun effectiveCooldownSeconds(now: java.util.Calendar = java.util.Calendar.getInstance()): Long {
        val minute = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val pattern = Regex("^(\\d{2}):(\\d{2})-(\\d{2}):(\\d{2}):(-?\\d+)$")
        for (part in cooldownByTime.split(';')) {
            val match = pattern.matchEntire(part.trim()) ?: continue
            val (sh, sm, eh, em, seconds) = match.destructured
            val start = sh.toInt() * 60 + sm.toInt()
            val end = eh.toInt() * 60 + em.toInt()
            if (start !in 0..1439 || end !in 0..1439 || start == end) continue
            val inSegment = if (start < end) minute in start until end else minute >= start || minute < end
            if (inSegment) return seconds.toLong().coerceAtLeast(-1)
        }
        return cooldownSeconds
    }
}

data class PushConfig(
    val scenes: Map<String, PushSceneConfig> = emptyMap(),
    val globalDailyLimit: Int = 0,
    val globalIntervalMillis: Long = 0,
    val channelName: String = "",
    val weather: PushWeatherData? = null,
    /** Null leaves host-supplied content unchanged; an empty list explicitly clears it. */
    val news: List<PushNewsItem>? = null,
    val hasWeather: Boolean = false,
) {
    fun scene(key: String): PushSceneConfig = scenes[key] ?: PushSceneConfig()
}
