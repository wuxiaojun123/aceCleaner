package com.nice.library_push.business.calendar

import android.content.Context
import com.nice.library_push.R
import com.nice.library_push.api.PushContent
import com.nice.library_push.api.PushEvent
import com.nice.library_push.api.PushStyle
import com.nice.library_push.api.PushTemplate
import com.nice.library_push.config.PushTemplateSpec
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local date card, like reference 904; does not invent appointments or read the user's calendar. */
class CalendarPushTemplate : PushTemplate {
    override val type = "calendar"

    override fun render(context: Context, event: PushEvent, spec: PushTemplateSpec): PushContent {
        val now = Date()
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        return PushContent(
            title = event.title.ifBlank { spec.title.ifBlank { context.getString(R.string.string_push_calendar_today) } },
            body = event.content.ifBlank { spec.content.ifBlank { context.getString(R.string.string_push_calendar_summary) } },
            button = spec.buttonText.ifBlank { context.getString(R.string.string_push_calendar_action) },
            target = event.target.ifBlank { spec.target }, style = PushStyle.CALENDAR,
            label = context.getString(R.string.string_push_calendar_today),
            date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(now),
            day = SimpleDateFormat("d", locale).format(now),
            weekday = SimpleDateFormat("EEEE", locale).format(now),
        )
    }
}
