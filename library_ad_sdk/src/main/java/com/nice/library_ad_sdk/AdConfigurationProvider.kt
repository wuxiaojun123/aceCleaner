package com.nice.library_ad_sdk

import android.content.Context
import androidx.annotation.RawRes
import com.cater.bi.a.AdConfigParser
import com.cater.bi.a.AdConfiguration
import com.nice.library_ad_sdk.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.sink
import okio.source

/** Loads the last valid online ad configuration, with `spaces.json` as fallback. */
class AdConfigurationProvider(
    context: Context,
    @param:RawRes private val bundledConfigResId: Int,
    private val remoteConfig: AdRemoteConfig?
) {
    data class CfgWrap(val version: String, val cfg: AdConfiguration)

    private val context = context.applicationContext

    private val configFileName by lazy {
        "abi-${context.packageName}".encodeUtf8().md5().hex()
    }
    private val preferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    var lastVersion: String = ""
        private set

    suspend fun get(): Result<CfgWrap> = withContext(Dispatchers.IO) {
        loadSaved().recoverCatching {
            LogUtils.w(tag = TAG, msg = "No valid saved ad configuration; using spaces.json", throwable = it)
            loadEmbedded().getOrThrow()
        }.onSuccess { lastVersion = it.version }
    }

    /** Fetches and stores a valid online configuration when one is available. */
    suspend fun refreshRemote(): Result<Boolean> = withContext(Dispatchers.IO) {
        val config = remoteConfig ?: return@withContext Result.success(false)
        config.fetchConfigurationJson().mapCatching { json ->
            if (json.isNullOrBlank()) {
                return@mapCatching false
            }

            // Validate before persisting so a bad server value cannot poison the cache.
            AdConfigParser.parseFromJson(json)
            val version = json.encodeUtf8().md5().hex()
            if (version == preferences.getString(KEY_VERSION, null)) {
                lastVersion = version
                return@mapCatching false
            }

            context.filesDir.resolve(configFileName).sink().buffer().use { sink ->
                sink.writeUtf8(json)
            }
            preferences.edit().putString(KEY_VERSION, version).apply()
            lastVersion = version
            LogUtils.d(tag = TAG, msg = "Stored remote ad configuration $version")
            true
        }
    }

    private fun loadSaved(): Result<CfgWrap> = runCatching {
        val saved = context.filesDir.resolve(configFileName)
        check(saved.exists()) { "No saved ad configuration" }
        val json = saved.source().buffer().use { it.readUtf8() }
        check(json.isNotBlank()) { "Saved ad configuration is empty" }
        val version = preferences.getString(KEY_VERSION, null)
            ?: json.encodeUtf8().md5().hex()
        CfgWrap(version, AdConfigParser.parseFromJson(json))
    }

    private fun loadEmbedded(): Result<CfgWrap> = runCatching {
        val json = context.resources.openRawResource(bundledConfigResId)
            .source()
            .buffer()
            .use { it.readUtf8() }
        check(json.isNotBlank()) { "Embedded ad configuration is empty" }
        val version = "embed-${json.encodeUtf8().md5().hex()}"
        CfgWrap(version, AdConfigParser.parseFromJson(json))
    }

    companion object {
        private const val TAG = "AdConfigProvider"
        private const val PREFERENCES_NAME = "ad_configuration"
        private const val KEY_VERSION = "version"
    }
}
