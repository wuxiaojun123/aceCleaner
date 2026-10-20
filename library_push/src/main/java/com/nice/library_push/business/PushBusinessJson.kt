package com.nice.library_push.business

import com.nice.library_push.api.PushNewsItem
import com.nice.library_push.api.PushWeatherData
import com.nice.library_push.api.WeatherCondition
import org.json.JSONArray
import org.json.JSONObject

internal object PushBusinessJson {
    fun weather(value: JSONObject): PushWeatherData = PushWeatherData(
        title = value.optString("title"),
        description = value.optString("description"),
        condition = WeatherCondition.entries.firstOrNull { it.name.equals(value.optString("condition"), true) }
            ?: WeatherCondition.CLOUDY,
        location = value.optString("location"),
        temperature = value.optString("temperature"),
        target = value.optString("target"),
        // Remote observations require real timestamps; repeatedly fetching cached data must not renew it.
        observedAtMillis = value.optLong("observedAtMillis", 0),
        expiresAtMillis = value.optLong("expiresAtMillis", 0),
    )

    fun news(array: JSONArray): List<PushNewsItem> = (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { value ->
            PushNewsItem(
                id = value.optString("id").ifBlank { value.optString("url") },
                title = value.optString("title"), url = value.optString("url"),
                description = value.optString("description"), imageUrl = value.optString("imageUrl"),
                source = value.optString("source"), publishedAtMillis = value.optLong("publishedAtMillis", 0),
                expiresAtMillis = value.optLong("expiresAtMillis", 0),
            )
        }
    }.distinctBy { it.id }

    fun encode(value: PushWeatherData): JSONObject = JSONObject()
        .put("title", value.title).put("description", value.description).put("condition", value.condition.name)
        .put("location", value.location).put("temperature", value.temperature).put("target", value.target)
        .put("observedAtMillis", value.observedAtMillis).put("expiresAtMillis", value.expiresAtMillis)

    fun encode(items: List<PushNewsItem>): JSONArray = JSONArray().apply {
        items.forEach { value ->
            put(JSONObject().put("id", value.id).put("title", value.title).put("url", value.url)
                .put("description", value.description).put("imageUrl", value.imageUrl).put("source", value.source)
                .put("publishedAtMillis", value.publishedAtMillis).put("expiresAtMillis", value.expiresAtMillis))
        }
    }
}
