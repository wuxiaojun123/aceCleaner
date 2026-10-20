package com.nice.library_push

import android.app.Application
import android.widget.FrameLayout
import android.view.View
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nice.library_push.api.*
import com.nice.library_push.business.PushBusinessStore
import com.nice.library_push.business.calendar.CalendarPushTemplate
import com.nice.library_push.business.news.NewsPushTemplate
import com.nice.library_push.business.weather.WeatherPushTemplate
import com.nice.library_push.config.PushTemplateSpec
import com.nice.library_push.display.PushPresenter
import com.nice.library_push.databinding.NicePushCalendarOverlayBinding
import com.nice.library_push.databinding.NicePushWeatherOverlayBinding
import com.nice.library_push.databinding.NicePushNewsOverlayBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushBusinessDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application

    @Test fun allTwelveBusinessRemoteViewsInflateAndBindWithoutRemoteViewErrors() {
        val now = System.currentTimeMillis()
        val templates = listOf(
            CalendarPushTemplate(),
            WeatherPushTemplate { PushWeatherData("Rain forecast", "Bring an umbrella", WeatherCondition.RAIN,
                observedAtMillis = now - 1000, expiresAtMillis = now + 60000) },
            NewsPushTemplate(next = { PushNewsItem("a", "Headline", "https://example.com/article", "Summary", source = "Publisher") }),
        )
        val presenter = PushPresenter(app)
        val previews = mutableListOf<Bitmap>()
        instrumentation.runOnMainSync {
            templates.forEach { template ->
                val content = template.render(app, PushEvent("app"), PushTemplateSpec(904))
                PushPresenter.Size.entries.forEach { size ->
                    val layout = presenter.layout(content.style, size)
                    val views = presenter.remoteViews(layout, content, PushClick("app", 904, 0, "content", content.target),
                        3101, null, false)
                    val inflated = views.apply(app, FrameLayout(app))
                    // The sizes intentionally share IDs, so the bindings validate every hierarchy.
                    val fields = when (content.style) {
                        PushStyle.CALENDAR -> NicePushCalendarOverlayBinding.bind(inflated).let { it.nicePushTitle to it.nicePushClose }
                        PushStyle.WEATHER -> NicePushWeatherOverlayBinding.bind(inflated).let { it.nicePushTitle to it.nicePushClose }
                        PushStyle.NEWS -> NicePushNewsOverlayBinding.bind(inflated).let { it.nicePushTitle to it.nicePushClose }
                        else -> error("Unexpected style")
                    }
                    assertEquals(content.title, fields.first.text.toString())
                    assertEquals(View.GONE, fields.second.visibility)
                    val width = (360 * app.resources.displayMetrics.density).toInt()
                    inflated.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    inflated.layout(0, 0, width, inflated.measuredHeight)
                    assertTrue(inflated.measuredHeight > 0)
                    if (size == PushPresenter.Size.LARGE) {
                        previews += Bitmap.createBitmap(width, inflated.measuredHeight, Bitmap.Config.ARGB_8888).also {
                            inflated.draw(Canvas(it))
                        }
                    }
                }
            }
        }
        val preview = Bitmap.createBitmap(previews.first().width, previews.sumOf { it.height + 24 }, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview)
        canvas.drawColor(Color.WHITE)
        var y = 0f
        previews.forEach { canvas.drawBitmap(it, 0f, y, null); y += it.height + 24 }
        java.io.File(app.filesDir, "push-business-preview.png").outputStream().use {
            preview.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun persistedNewsRotatesOnlyAfterSuccessfulDisplayAndSkipsExpiredItems() {
        app.getSharedPreferences("nice_push_business", 0).edit().clear().commit()
        try {
            val store = PushBusinessStore(app)
            val first = PushNewsItem("a", "A", "https://example.com/a")
            val second = PushNewsItem("b", "B", "https://example.com/b")
            store.setNews(listOf(first.copy(id = "expired", expiresAtMillis = 1), first, second))
            assertEquals(first, store.nextNews())
            assertEquals(first, store.nextNews())
            store.recordNewsShown(first.id)
            assertEquals(second, PushBusinessStore(app).nextNews())
            store.recordNewsShown(second.id)
            assertEquals(first, store.nextNews())
            store.setNews(emptyList())
            assertNull(PushBusinessStore(app).nextNews())
        } finally {
            app.getSharedPreferences("nice_push_business", 0).edit().clear().commit()
        }
    }
}
