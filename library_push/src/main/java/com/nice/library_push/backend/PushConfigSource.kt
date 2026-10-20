package com.nice.library_push.backend

import java.net.HttpURLConnection
import java.net.URL

/** The host controls authentication and endpoint. The library never embeds server credentials. */
fun interface PushConfigSource {
    suspend fun fetch(): String
}

class HttpPushConfigSource(
    private val url: String,
    private val headers: () -> Map<String, String> = { emptyMap() },
) : PushConfigSource {
    override suspend fun fetch(): String {
        require(url.startsWith("https://")) { "Push configuration requires HTTPS" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            headers().forEach { (key, value) -> connection.setRequestProperty(key, value) }
            check(connection.responseCode in 200..299) { "Push config HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
