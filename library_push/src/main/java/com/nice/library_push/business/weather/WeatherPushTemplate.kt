package com.nice.library_push.business.weather

import android.content.Context
import com.nice.library_push.R
import com.nice.library_push.api.*
import com.nice.library_push.config.PushTemplateSpec
import java.text.DateFormat
import java.util.Date

class WeatherPushTemplate(
    private val data: () -> PushWeatherData? = { null },
) : PushTemplate {
    override val type = "weather"
    override fun isAvailable(event: PushEvent, spec: PushTemplateSpec) = data()?.isAvailable() == true

    override fun render(context: Context, event: PushEvent, spec: PushTemplateSpec): PushContent {
        val weather = requireNotNull(data()?.takeIf { it.isAvailable() }) { "No current weather observation" }
        return PushContent(
            title = weather.title,
            body = weather.description,
            button = spec.buttonText.ifBlank { context.getString(R.string.string_push_weather_action) },
            target = weather.target.ifBlank { spec.target }, style = PushStyle.WEATHER,
            label = listOf(weather.location, weather.temperature).filter { it.isNotBlank() }.joinToString(" · "),
            date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
                context.resources.configuration.locales[0]).format(Date(weather.observedAtMillis)),
            weatherCondition = weather.condition,
            expiresAtMillis = weather.expiresAtMillis,
        )
    }
}
