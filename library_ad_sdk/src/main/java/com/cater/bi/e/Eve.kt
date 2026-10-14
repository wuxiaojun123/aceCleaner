package com.cater.bi.e

import com.nice.library_ad_sdk.util.LogUtils
import java.util.concurrent.CopyOnWriteArrayList

/** App-local event bridge that forwards events to Firebase Analytics. */
object Eve {
    private const val TAG = "AdEvent"
    private val backends: List<Evend> = listOf(FirebaseEv.instance)
    private val interceptors = CopyOnWriteArrayList<EvInterceptor>()

    init {
        addInterceptor(BasePropInterceptor())
    }

    fun addInterceptor(interceptor: EvInterceptor) {
        interceptors.addIfAbsent(interceptor)
    }

    fun removeInterceptor(interceptor: EvInterceptor) {
        interceptors.remove(interceptor)
    }

    fun send(event: String, properties: Map<String, Any>) {
        val values = properties.toMutableMap()
        for (interceptor in interceptors) {
            val result = runCatching {
                interceptor.onSend(event, values)
            }
            if (result.isFailure) {
                LogUtils.w(tag = TAG, msg = "Event interceptor failed", throwable = result.exceptionOrNull())
                continue
            }
            if (result.getOrThrow().isFailure) {
                return
            }
        }
        backends.forEach { backend ->
            runCatching { backend.send(event, values) }
                .onFailure { LogUtils.e(tag = TAG, msg = "Failed to send $event to Firebase", throwable = it) }
        }
        LogUtils.d(tag = TAG, msg = "$event: $values")
    }
}
