package com.nice.library_push.business.file

import android.content.Context
import com.nice.library_push.api.PushContent
import com.nice.library_push.api.PushEvent
import com.nice.library_push.api.PushTemplate
import com.nice.library_push.config.PushTemplateSpec
import com.nice.library_push.R

class FilePushTemplate : PushTemplate {
    override val type = "file"
    override fun render(context: Context, event: PushEvent, spec: PushTemplateSpec) = PushContent(
        title = event.title.ifBlank { spec.title.ifBlank { context.getString(R.string.nice_push_file_update) } },
        body = spec.content.ifBlank { event.content.ifBlank { context.getString(R.string.nice_push_file_changed) } },
        button = spec.buttonText.ifBlank { context.getString(R.string.nice_push_open) },
        imageUrl = spec.imageUrls.firstOrNull().orEmpty(),
        target = event.target.ifBlank { spec.target },
    )
}
