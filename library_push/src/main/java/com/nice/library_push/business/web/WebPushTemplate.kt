package com.nice.library_push.business.web

import android.content.Context
import com.nice.library_push.api.PushContent
import com.nice.library_push.api.PushEvent
import com.nice.library_push.api.PushTemplate
import com.nice.library_push.config.PushTemplateSpec
import com.nice.library_push.R

class WebPushTemplate : PushTemplate {
    override val type = "web"
    override fun render(context: Context, event: PushEvent, spec: PushTemplateSpec) = PushContent(
        title = event.title.ifBlank { spec.title.ifBlank { context.getString(R.string.nice_push_web) } },
        body = event.content.ifBlank { spec.content },
        button = spec.buttonText.ifBlank { context.getString(R.string.nice_push_view) },
        imageUrl = event.imageUrl.ifBlank { spec.imageUrls.firstOrNull().orEmpty() },
        target = event.target.ifBlank { spec.target },
    )
}
