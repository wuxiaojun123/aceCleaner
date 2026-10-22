package com.nice.library_push.business.battery

import android.content.Context
import com.nice.library_push.R
import com.nice.library_push.api.PushContent
import com.nice.library_push.api.PushEvent
import com.nice.library_push.api.PushTemplate
import com.nice.library_push.config.PushTemplateSpec

internal class BatteryPushTemplate : PushTemplate {
    override val type: String = "battery"

    override fun isAvailable(event: PushEvent, spec: PushTemplateSpec): Boolean =
        event.metadata["level"]?.toIntOrNull() in 0..100

    override fun render(context: Context, event: PushEvent, spec: PushTemplateSpec): PushContent =
        PushContent(
            title = context.getString(R.string.string_push_battery_title),
            body = context.getString(R.string.string_push_battery_remaining, event.metadata.getValue("level").toInt()),
            button = context.getString(R.string.nice_push_open),
            target = spec.target,
        )
}
