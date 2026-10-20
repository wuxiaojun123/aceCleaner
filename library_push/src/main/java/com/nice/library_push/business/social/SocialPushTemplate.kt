package com.nice.library_push.business.social

import android.content.Context
import com.nice.library_push.api.PushContent
import com.nice.library_push.api.PushEvent
import com.nice.library_push.api.PushTemplate
import com.nice.library_push.config.PushTemplateSpec
import com.nice.library_push.R

/** The host may use target as an app package or an internal route. */
class SocialPushTemplate : PushTemplate {
    override val type = "social"
    override fun render(context: Context, event: PushEvent, spec: PushTemplateSpec) = PushContent(
        title = event.title.ifBlank { spec.title.ifBlank { context.getString(R.string.nice_push_social) } },
        body = event.content.ifBlank { spec.content },
        button = spec.buttonText.ifBlank { context.getString(R.string.nice_push_open) },
        imageUrl = event.imageUrl.ifBlank { spec.imageUrls.firstOrNull().orEmpty() },
        target = event.target.ifBlank { spec.target },
    )
}
