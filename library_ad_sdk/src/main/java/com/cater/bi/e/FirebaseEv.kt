package com.cater.bi.e

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics

/** Sends app-local events to Firebase Analytics / GA4. */
class FirebaseEv private constructor() : Evend {

    companion object {
        val instance = FirebaseEv()
    }

    private val analytics by lazy { Firebase.analytics }

    override fun name(): String = "firebase"

    override fun send(event: String, properties: Map<String, Any>) {
        analytics.logEvent(event, Bundle().apply {
            properties.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Double -> putDouble(key, value)
                    is Float -> putDouble(key, value.toDouble())
                    is Int -> putLong(key, value.toLong())
                    is Long -> putLong(key, value)
                    is Number -> putDouble(key, value.toDouble())
                    is String -> putString(key, value)
                    is Bundle -> putBundle(key, value)
                    else -> putString(key, value.toString())
                }
            }
        })
    }
}
