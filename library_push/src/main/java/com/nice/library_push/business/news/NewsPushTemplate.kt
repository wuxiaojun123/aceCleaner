package com.nice.library_push.business.news

import android.content.Context
import com.nice.library_push.api.PushContent
import com.nice.library_push.api.PushEvent
import com.nice.library_push.api.PushTemplate
import com.nice.library_push.config.PushTemplateSpec
import com.nice.library_push.R
import com.nice.library_push.api.PushNewsItem
import com.nice.library_push.api.PushStyle
import com.nice.library_push.api.isHttpsUrl
import java.text.DateFormat
import java.util.Date

/** News fields can come from the host's backend event or scene template. */
class NewsPushTemplate(
    private val next: () -> PushNewsItem? = { null },
    private val recordShown: (String) -> Unit = {},
) : PushTemplate {
    override val type = "news"
    override fun isAvailable(event: PushEvent, spec: PushTemplateSpec) =
        explicitItem(event, spec)?.isAvailable() == true || next()?.isAvailable() == true

    override fun render(context: Context, event: PushEvent, spec: PushTemplateSpec): PushContent {
        val item = requireNotNull(explicitItem(event, spec)?.takeIf { it.isAvailable() } ?: next()?.takeIf { it.isAvailable() })
        return PushContent(
            title = item.title, body = item.description,
            button = spec.buttonText.ifBlank { context.getString(R.string.string_push_news_read) },
            imageUrl = item.imageUrl, target = item.url, style = PushStyle.NEWS,
            label = item.source.ifBlank { context.getString(R.string.string_push_news_label) },
            date = if (item.publishedAtMillis > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
                context.resources.configuration.locales[0]).format(Date(item.publishedAtMillis)) else "",
            statisticsId = item.id,
            expiresAtMillis = item.expiresAtMillis,
        )
    }

    override fun onShown(content: PushContent) { recordShown(content.statisticsId) }

    private fun explicitItem(event: PushEvent, spec: PushTemplateSpec): PushNewsItem? {
        val target = event.target.ifBlank { spec.target }
        val title = event.title.ifBlank { spec.title }
        if (!isHttpsUrl(target) || title.isBlank()) return null
        return PushNewsItem(
            id = event.metadata["newsId"] ?: target, title = title, url = target,
            description = event.content.ifBlank { spec.content },
            imageUrl = event.imageUrl.ifBlank { spec.imageUrls.firstOrNull().orEmpty() },
            source = event.metadata["source"].orEmpty(),
            publishedAtMillis = event.metadata["publishedAtMillis"]?.toLongOrNull() ?: 0,
            expiresAtMillis = event.metadata["expiresAtMillis"]?.toLongOrNull() ?: 0,
        )
    }
}
