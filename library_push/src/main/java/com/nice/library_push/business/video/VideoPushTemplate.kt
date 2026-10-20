package com.nice.library_push.business.video

import android.content.Context
import com.nice.library_push.api.PushContent
import com.nice.library_push.api.PushEvent
import com.nice.library_push.api.PushTemplate
import com.nice.library_push.config.PushTemplateSpec
import com.nice.library_push.R

class VideoPushTemplate : PushTemplate {
    override val type = "video"
    override fun isAvailable(event: PushEvent, spec: PushTemplateSpec) = event.title.isNotBlank() || spec.content.isNotBlank()
    override fun render(context: Context, event: PushEvent, spec: PushTemplateSpec) = PushContent(
        title = event.title.ifBlank { spec.title.ifBlank { context.getString(R.string.nice_push_video) } },
        body = event.content.ifBlank { spec.content },
        button = spec.buttonText.ifBlank { context.getString(R.string.nice_push_watch) },
        imageUrl = event.imageUrl.ifBlank { spec.imageUrls.firstOrNull().orEmpty() },
        target = event.target.ifBlank { spec.target },
    )
}
