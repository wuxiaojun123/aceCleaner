package com.nice.library_push

import com.nice.library_push.api.PushNewsItem
import com.nice.library_push.api.PushWeatherData
import com.nice.library_push.api.WeatherCondition
import com.nice.library_push.business.PushBusinessJson
import com.nice.library_push.config.PushConfigParser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PushBusinessConfigTest {
    @Test fun legacyIdsSelectTheActualBusinessTemplates() {
        val config = PushConfigParser.parse("""{"app":{"sts":1,"temps":[
            {"tempId":904,"wgt":100},{"tempId":905,"wgt":100},{"tempId":910,"wgt":100}
        ]}}""")
        assertEquals(listOf("calendar", "weather", "news"), config.scene("app").templates.map { it.type })
    }

    @Test fun explicitTypeOverridesLegacyIdAndOverlayCloseIsIndependent() {
        val config = PushConfigParser.parse("""{"scenes":{"app":{"temps":[
            {"tempId":904,"type":"news","cx":0,"overlayCx":1}
        ]}}}""")
        val template = config.scene("app").templates.single()
        assertEquals("news", template.type)
        assertFalse(template.showClose)
        assertTrue(template.showOverlayClose)
    }

    @Test fun absentBusinessDataDoesNotClearHostContentButExplicitEmptyDoes() {
        val absent = PushConfigParser.parse("{}")
        assertFalse(absent.hasWeather)
        assertNull(absent.news)
        val clear = PushConfigParser.parse("""{"weather":null,"news":[]}""")
        assertTrue(clear.hasWeather)
        assertNull(clear.weather)
        assertEquals(emptyList<PushNewsItem>(), clear.news)
    }

    @Test fun weatherRequiresAnUnexpiredRealObservation() {
        val data = PushWeatherData("Forecast", "Rain", WeatherCondition.RAIN,
            observedAtMillis = 1000, expiresAtMillis = 2000)
        assertFalse(data.isAvailable(999))
        assertTrue(data.isAvailable(1000))
        assertFalse(data.isAvailable(2000))
        assertFalse(data.copy(title = "").isAvailable(1500))
        val missingTime = PushBusinessJson.weather(JSONObject("""{"title":"Rain","condition":"rain"}"""))
        assertFalse(missingTime.isAvailable())
    }

    @Test fun cachedWeatherDoesNotGainANewExpiryOnDecode() {
        val data = PushWeatherData("Forecast", "Rain", WeatherCondition.STORM, "City", "20°C", "weather",
            observedAtMillis = 1000, expiresAtMillis = 2000)
        assertEquals(data, PushBusinessJson.weather(JSONObject(PushBusinessJson.encode(data).toString())))
    }

    @Test fun newsRejectsExpiredFutureAndInvalidDestinations() {
        val item = PushNewsItem("id", "Story", "https://example.com/news", publishedAtMillis = 1000, expiresAtMillis = 2000)
        assertTrue(item.isAvailable(1500))
        assertFalse(item.isAvailable(2000))
        assertFalse(item.isAvailable(999))
        listOf("javascript:alert(1)", "file:///private", "https:///news", "https://user@example.com").forEach {
            assertFalse(item.copy(url = it).isAvailable(1500))
        }
    }

    @Test fun newsConfigRoundTripsContentAndDeduplicatesIds() {
        val item = PushNewsItem("a", "Story", "https://example.com/article", "Summary", "https://example.com/image",
            "Publisher", 1000, 2000)
        val config = PushConfigParser.parse(JSONObject().put("news", PushBusinessJson.encode(listOf(item, item))).toString())
        assertEquals(listOf(item), config.news)
    }
}
