package com.nice.library_push.api

import android.content.Context
import com.nice.library_push.config.PushTemplateSpec

data class PushEvent(
    val scene: String,
    val subType: Int = 0,
    val title: String = "",
    val content: String = "",
    val imageUrl: String = "",
    val target: String = "",
    val metadata: Map<String, String> = emptyMap(),
    /** Optional business filter; scene frequency limits still apply. */
    val templateType: String = "",
)

data class PushClick(
    val scene: String,
    val tempId: Int,
    val contentId: Int,
    val action: String,
    val target: String,
    val type: String = "default",
    val autoCancel: Boolean = true,
)

fun interface PushRouter { fun open(context: Context, click: PushClick) }

fun interface PushTracker { fun track(event: String, attributes: Map<String, String>) }

interface PushTemplate {
    val type: String
    fun isAvailable(event: PushEvent, spec: PushTemplateSpec): Boolean = true
    fun render(context: Context, event: PushEvent, spec: PushTemplateSpec): PushContent
    fun onShown(content: PushContent) = Unit
}

enum class PushStyle { DEFAULT, CALENDAR, WEATHER, NEWS }

data class PushContent(
    val title: String,
    val body: String,
    val button: String,
    val imageUrl: String = "",
    val target: String = "",
    val style: PushStyle = PushStyle.DEFAULT,
    val label: String = "",
    val date: String = "",
    val day: String = "",
    val weekday: String = "",
    val weatherCondition: WeatherCondition = WeatherCondition.CLOUDY,
    val statisticsId: String = "",
    val expiresAtMillis: Long = 0,
)
