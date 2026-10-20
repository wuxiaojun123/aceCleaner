package com.nice.library_push.config

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import com.nice.library_push.business.PushBusinessJson

/** Reads the reference scene keys and a plain JSON envelope from a host backend. */
object PushConfigParser {
    fun parse(raw: String): PushConfig {
        val trimmed = raw.trim()
        val json = if (trimmed.startsWith("{")) trimmed else
            String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
        val root = JSONObject(json)
        val scenesObject = root.optJSONObject("scenes") ?: root
        val scenes = PushScene.ALL.mapNotNull { key ->
            scenesObject.optJSONObject(key)?.let { key to parseScene(it) }
        }.toMap()
        return PushConfig(
            scenes = scenes,
            globalDailyLimit = root.optInt("globalDailyLimit", 0).coerceAtLeast(0),
            globalIntervalMillis = root.optLong("globalIntervalMillis", 0).coerceAtLeast(0),
            channelName = root.optString("channelName", ""),
            weather = root.optJSONObject("weather")?.let(PushBusinessJson::weather),
            news = root.optJSONArray("news")?.let(PushBusinessJson::news),
            hasWeather = root.has("weather"),
        )
    }

    private fun parseScene(value: JSONObject): PushSceneConfig {
        val templates = value.optJSONArray("temps") ?: JSONArray()
        return PushSceneConfig(
            enabled = value.optInt("sts", 0) == 1,
            cooldownSeconds = value.optLong("cd", 0).coerceAtLeast(0),
            cooldownByTime = value.optString("cdt", ""),
            installDelaySeconds = value.optLong("dly", 0).coerceAtLeast(0),
            organicDelaySeconds = value.optLong("org_dly", 0),
            dailyLimit = value.optInt("dcm", 0).coerceAtLeast(0),
            excludeVip = value.optInt("vip", 0) == 1,
            displayMode = when (value.optInt("display_type", 1)) {
                0 -> PushDisplayMode.OVERLAY_PREFERRED
                2 -> PushDisplayMode.OVERLAY_ONLY
                else -> PushDisplayMode.NOTIFICATION
            },
            batteryLevels = intList(value.opt("levels")),
            fileSuffixes = stringList(value.opt("suffixes")).map { it.trim().trimStart('.').lowercase() }.filter { it.isNotBlank() },
            templates = (0 until templates.length()).mapNotNull { i ->
                templates.optJSONObject(i)?.let(::parseTemplate)
            },
        )
    }

    private fun parseTemplate(value: JSONObject) = PushTemplateSpec(
        tempId = value.optInt("tempId", 0),
        type = value.optString("type", "").ifBlank { typeForTemplateId(value.optInt("tempId", 0)) },
        weight = value.optInt("wgt", 0).coerceAtLeast(0),
        subType = value.optInt("subType", 0),
        contentId = value.optInt("contentId", 0),
        title = value.optString("title", ""),
        content = value.optString("content", ""),
        buttonText = value.optString("btnText", ""),
        imageUrls = stringList(value.opt("imageList")),
        target = value.optString("target", ""),
        autoCancel = value.optBoolean("autoCancel", true),
        ongoing = value.optBoolean("ongoing", false),
        showClose = value.optInt("cx", 1) != 0,
        showOverlayClose = value.optInt("overlayCx", 1) != 0,
    )

    fun typeForTemplateId(id: Int): String = when (id) {
        904 -> "calendar"
        905 -> "weather"
        906 -> "image"
        907 -> "file"
        910 -> "news"
        920 -> "video"
        940 -> "social"
        950 -> "web"
        else -> "default"
    }

    private fun stringList(value: Any?): List<String> = when (value) {
        is JSONArray -> (0 until value.length()).map { value.optString(it) }.filter { it.isNotBlank() }
        is String -> value.split(',').map(String::trim).filter(String::isNotBlank)
        else -> emptyList()
    }

    private fun intList(value: Any?): List<Int> = stringList(value).mapNotNull(String::toIntOrNull)
}
