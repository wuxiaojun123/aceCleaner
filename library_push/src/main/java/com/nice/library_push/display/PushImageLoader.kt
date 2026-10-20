package com.nice.library_push.display

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import android.util.LruCache
import android.os.SystemClock
import com.nice.library_push.api.isHttpsUrl

internal object PushImageLoader {
    private data class CachedImage(val bitmap: Bitmap, val loadedAt: Long)
    private val cache = object : LruCache<String, CachedImage>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CachedImage) = value.bitmap.byteCount
    }

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!isHttpsUrl(url)) return@withContext null
        cache.get(url)?.takeIf { SystemClock.elapsedRealtime() - it.loadedAt < 30 * 60 * 1000L }?.let {
            return@withContext it.bitmap
        }
        val bitmap = runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.instanceFollowRedirects = false
                if (connection.responseCode !in 200..299) return@runCatching null
                val length = connection.contentLengthLong
                if (length > 2_000_000) return@runCatching null
                val bytes = connection.inputStream.use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= 2_000_000) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                if (bytes.size > 2_000_000) null else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                }
            } finally { connection.disconnect() }
        }.getOrNull()
        if (bitmap != null) cache.put(url, CachedImage(bitmap, SystemClock.elapsedRealtime()))
        bitmap
    }
}
