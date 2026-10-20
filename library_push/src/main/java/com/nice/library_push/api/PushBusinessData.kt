package com.nice.library_push.api

/** These values select artwork, never imply that a weather observation has occurred. */
enum class WeatherCondition { RAIN, STORM, HOT, CLEAR, CLOUDY }

data class PushWeatherData(
    val title: String,
    val description: String,
    val condition: WeatherCondition,
    val location: String = "",
    val temperature: String = "",
    val target: String = "",
    val observedAtMillis: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long = observedAtMillis + 3 * 60 * 60 * 1000L,
) {
    fun isAvailable(now: Long = System.currentTimeMillis()): Boolean =
        title.isNotBlank() && observedAtMillis > 0 && observedAtMillis <= now && expiresAtMillis > now
}

data class PushNewsItem(
    val id: String,
    val title: String,
    val url: String,
    val description: String = "",
    val imageUrl: String = "",
    val source: String = "",
    val publishedAtMillis: Long = 0,
    /** Zero means the backend did not specify an expiry. */
    val expiresAtMillis: Long = 0,
) {
    fun isAvailable(now: Long = System.currentTimeMillis()): Boolean =
        id.isNotBlank() && title.isNotBlank() && isHttpsUrl(url) &&
            (expiresAtMillis == 0L || expiresAtMillis > now) && publishedAtMillis <= now
}

internal fun isHttpsUrl(value: String): Boolean = runCatching {
    java.net.URI(value).let { it.scheme.equals("https", true) && !it.host.isNullOrBlank() && it.userInfo == null }
}.getOrDefault(false)
