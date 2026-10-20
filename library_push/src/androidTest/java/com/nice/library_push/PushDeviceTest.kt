package com.nice.library_push

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nice.library_push.api.PushClick
import com.nice.library_push.api.PushLibrary
import com.nice.library_push.api.PushRouter
import com.nice.library_push.api.PushTracker
import com.nice.library_push.api.PushWeatherData
import com.nice.library_push.api.PushNewsItem
import com.nice.library_push.api.WeatherCondition
import com.nice.library_push.backend.PushConfigSource
import com.nice.library_push.config.PushConfigParser
import com.nice.library_push.config.PushScene
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class PushDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before fun setUp() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() }
            }
        }
        PushLibrary.destroy()
        notifications.cancelAll()
        app.getSharedPreferences("nice_push_library", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() {
        PushLibrary.destroy()
        notifications.cancelAll()
    }

    @Test fun parsesRemoteTextAndSceneSettings() {
        val config = PushConfigParser.parse(CONFIG)
        assertEquals(1, config.globalDailyLimit)
        assertEquals(1, config.scene(PushScene.APP).dailyLimit)
        assertEquals("Device test title", config.scene(PushScene.APP).templates.single().title)
    }

    @Test fun postsNotificationRoutesClickAndEnforcesDailyLimit() {
        val events = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        val clicks = CopyOnWriteArrayList<PushClick>()
        PushLibrary.initialize(
            app,
            PushConfigSource { CONFIG },
            PushRouter { _, click -> clicks += click },
            PushTracker { event, attributes -> events += event to attributes },
        )
        assertTrue("Remote config did not load", waitUntil { PushLibrary.currentConfig()?.scene(PushScene.APP)?.enabled == true })

        PushLibrary.triggerAppScene()
        assertTrue("Notification was not posted", waitUntil { notifications.activeNotifications.any { it.notification.extras.getString("android.title") == "Device test title" } })
        val posted = notifications.activeNotifications.single { it.notification.extras.getString("android.title") == "Device test title" }
        assertEquals("Device test body", posted.notification.extras.getString("android.text"))

        PushLibrary.triggerAppScene()
        assertTrue("Second trigger did not hit daily cap", waitUntil {
            events.any { (event, attributes) -> event == "notification_skipped" && attributes["reason"] == "daily_cap" }
        })
        assertEquals(1, events.count { it.first == "notification_shown" })

        posted.notification.contentIntent.send()
        assertTrue("Notification click did not route", waitUntil { clicks.size == 1 })
        assertEquals(PushScene.APP, clicks.single().scene)
        assertEquals("content", clicks.single().action)
        assertEquals("test-target", clicks.single().target)
        assertTrue("Clicked notification was not removed", waitUntil { notifications.activeNotifications.none { it.id == posted.id } })
    }

    @Test fun businessTriggersSelectTheirOwnLayoutsAndPreserveClickPolicy() {
        val clicks = CopyOnWriteArrayList<PushClick>()
        PushLibrary.initialize(app, PushConfigSource { """{"scenes":{"app":{"sts":1,"display_type":1,"temps":[
            {"tempId":904,"wgt":100},
            {"tempId":905,"wgt":100,"autoCancel":false},
            {"tempId":910,"wgt":100}
        ]}}}""" }, PushRouter { _, click -> clicks += click })
        assertTrue(waitUntil { PushLibrary.currentConfig()?.scene("app")?.enabled == true })
        val now = System.currentTimeMillis()
        PushLibrary.setWeather(PushWeatherData("Weather observation", "Rain", WeatherCondition.RAIN,
            observedAtMillis = now - 1000, expiresAtMillis = now + 60000))
        PushLibrary.setNews(listOf(PushNewsItem("story", "News headline", "https://example.com/story")))
        PushLibrary.triggerWeather()
        assertTrue(waitUntil { notifications.activeNotifications.any { it.notification.contentView.layoutId == R.layout.nice_push_weather_small } })
        val weather = notifications.activeNotifications.single { it.notification.contentView.layoutId == R.layout.nice_push_weather_small }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor("com.nice.library_push.display.PushClickActivity", null, false)
        weather.notification.contentIntent.send()
        val clickActivity = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
        instrumentation.removeMonitor(monitor)
        assertTrue("Click activity did not finish", waitUntil { clickActivity?.isDestroyed == true })
        assertTrue(waitUntil { clicks.size == 1 })
        assertEquals("weather", clicks.single().type)
        assertEquals(false, clicks.single().autoCancel)
        assertTrue(notifications.activeNotifications.any { it.id == weather.id })
        PushLibrary.triggerNews()
        assertTrue(waitUntil { notifications.activeNotifications.any { it.notification.contentView.layoutId == R.layout.nice_push_news_small } })
        PushLibrary.triggerCalendar()
        assertTrue(waitUntil { notifications.activeNotifications.any { it.notification.contentView.layoutId == R.layout.nice_push_calendar_small } })
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        repeat(100) {
            if (condition()) return true
            SystemClock.sleep(50)
        }
        return condition()
    }

    companion object {
        private const val CONFIG = """{
            "globalDailyLimit":1,
            "channelName":"Device test",
            "scenes":{"app":{
                "sts":1,"dcm":1,"display_type":1,
                "temps":[{"tempId":7,"type":"default","wgt":100,
                    "title":"Device test title","content":"Device test body",
                    "btnText":"Open","target":"test-target"}]
            }}
        }"""
    }
}
