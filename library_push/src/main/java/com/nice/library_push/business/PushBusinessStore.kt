package com.nice.library_push.business

import android.content.Context
import com.nice.library_push.api.PushNewsItem
import com.nice.library_push.api.PushWeatherData
import org.json.JSONArray
import org.json.JSONObject

/** Persistent content and next-news cursor are owned by one SDK runtime. */
internal class PushBusinessStore(context: Context) {
    private val prefs = context.getSharedPreferences("nice_push_business", Context.MODE_PRIVATE)
    private var weather = runCatching {
        prefs.getString("weather", null)?.let { PushBusinessJson.weather(JSONObject(it)) }
    }.getOrNull()
    private var news = runCatching {
        PushBusinessJson.news(JSONArray(prefs.getString("news", "[]")))
    }.getOrDefault(emptyList())

    @Synchronized fun setWeather(value: PushWeatherData?) {
        weather = value
        prefs.edit().putString("weather", value?.let { PushBusinessJson.encode(it).toString() }).apply()
    }

    @Synchronized fun setNews(items: List<PushNewsItem>) {
        news = items.distinctBy { it.id }
        prefs.edit().putString("news", PushBusinessJson.encode(news).toString()).apply()
    }

    @Synchronized fun weather(): PushWeatherData? = weather?.takeIf { it.isAvailable() }

    @Synchronized fun nextNews(): PushNewsItem? {
        val available = news.filter { it.isAvailable() }
        if (available.isEmpty()) return null
        val previous = prefs.getString("last_news_id", null)
        val index = available.indexOfFirst { it.id == previous }
        return available[(index + 1) % available.size]
    }

    @Synchronized fun recordNewsShown(id: String) {
        prefs.edit().putString("last_news_id", id).apply()
    }
}
