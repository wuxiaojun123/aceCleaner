package com.nice.library_ad_sdk

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.ByteString.Companion.decodeBase64

/**
 * Firebase Remote Config entry for the advertising configuration.
 *
 * Set [KEY_AD_CONFIGURATION] to either the JSON used by `res/raw/spaces.json`,
 * or to a Base64 encoded copy of that JSON. An empty/invalid remote value is
 * ignored and the embedded configuration remains active.
 */
class AdRemoteConfig(
    private val configurationKey: String,
    debug: Boolean,
    private val firebase: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
) {

    init {
        firebase.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(if (debug) 0L else 3_600L)
                .build()
        )
    }

    suspend fun fetchConfigurationJson(): Result<String?> = runCatching {
        awaitFetchAndActivate()
        firebase.getString(configurationKey).toConfigurationJsonOrNull()
    }

    private suspend fun awaitFetchAndActivate() {
        suspendCancellableCoroutine<Unit> { continuation ->
            firebase.fetchAndActivate().addOnCompleteListener { task ->
                if (!continuation.isActive) {
                    return@addOnCompleteListener
                }
                if (task.isSuccessful) {
                    continuation.resumeWith(Result.success(Unit))
                } else {
                    continuation.resumeWith(
                        Result.failure(task.exception ?: IllegalStateException("Remote config fetch failed"))
                    )
                }
            }
        }
    }

    private fun String.toConfigurationJsonOrNull(): String? {
        val value = trim()
        if (value.isEmpty()) {
            return null
        }
        if (value.startsWith("{")) {
            return value
        }
        return value.decodeBase64()?.utf8()?.trim()?.takeIf { it.startsWith("{") }
    }

    companion object {
        const val KEY_AD_CONFIGURATION = "abi_config_raw"
    }
}
