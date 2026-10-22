package com.nice.library_push.business

import android.content.Context
import com.nice.library_push.api.PushContent
import com.nice.library_push.api.PushEvent
import com.nice.library_push.api.PushTemplate
import com.nice.library_push.config.PushTemplateSpec
import com.nice.library_push.business.file.FilePushTemplate
import com.nice.library_push.business.news.NewsPushTemplate
import com.nice.library_push.business.web.WebPushTemplate
import com.nice.library_push.business.video.VideoPushTemplate
import com.nice.library_push.business.social.SocialPushTemplate
import com.nice.library_push.business.calendar.CalendarPushTemplate
import com.nice.library_push.business.weather.WeatherPushTemplate
import com.nice.library_push.api.PushNewsItem
import com.nice.library_push.api.PushWeatherData
import com.nice.library_push.business.battery.BatteryPushTemplate

/** Business templates remain independent of triggers and backend transport. */
object PushTemplates {
    fun defaults(
        weather: () -> PushWeatherData? = { null },
        news: () -> PushNewsItem? = { null },
        onNewsShown: (String) -> Unit = {},
    ): List<PushTemplate> = listOf(
        BasicTemplate("default"), BasicTemplate("image"),
        BatteryPushTemplate(),
        CalendarPushTemplate(), WeatherPushTemplate(weather), NewsPushTemplate(news, onNewsShown),
        WebPushTemplate(), VideoPushTemplate(), SocialPushTemplate(), FilePushTemplate(),
    )
}

private class BasicTemplate(override val type: String) : PushTemplate {
    override fun render(context: Context, event: PushEvent, spec: PushTemplateSpec): PushContent {
        return PushContent(
            title = event.title.ifBlank { spec.title.ifBlank { context.applicationInfo.loadLabel(context.packageManager).toString() } },
            body = event.content.ifBlank { spec.content },
            button = spec.buttonText,
            imageUrl = event.imageUrl.ifBlank { spec.imageUrls.firstOrNull().orEmpty() },
            target = event.target.ifBlank { spec.target },
        )
    }
}
