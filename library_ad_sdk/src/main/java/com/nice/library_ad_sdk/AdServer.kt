package com.nice.library_ad_sdk

import android.app.Application
import android.content.Context
import com.cater.bi.analyzer.AdAnalyzer
import com.cater.bi.a.AdErrorC
import com.cater.bi.a.AdRegistrationCallback
import com.cater.bi.a.Adm
import com.nice.library_ad_sdk.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 *
 * created on 2025/8/23
 * @author holmes
 */
object AdServer {

    private const val TAG = "ads"

    private lateinit var configurationProvider: AdConfigurationProvider
    private var appliedVersion: String = ""
    private val applyMux = Mutex()
    private val appliedCount = MutableStateFlow(0)
    val scope = MainScope()

    fun initialize(
        context: Context,
        bundledConfigResId: Int,
        remoteConfigKey: String?,
        debug: Boolean
    ) {
        if (Adm.isInitialized()) {
            return
        }

        val application = context as? Application ?: context.applicationContext as Application
        AdAnalyzer.initialize(application)
        val remoteConfig = remoteConfigKey?.let { key ->
            runCatching { AdRemoteConfig(key, debug) }
                .onFailure { LogUtils.w(tag = TAG, msg = "Firebase Remote Config is unavailable", throwable = it) }
                .getOrNull()
        }
        configurationProvider = AdConfigurationProvider(
            context = application,
            bundledConfigResId = bundledConfigResId,
            remoteConfig = remoteConfig
        )

        Adm.debug = debug
        Adm.init(application, true)
        Adm.updatePlatformExtras(mapOf(
            "channel" to "google"
        ))

        scope.launch(Dispatchers.IO) {
            // Apply cached/local configuration first; startup never waits on network.
            runCatching { applyAdConfig() }
                .onFailure { LogUtils.w(tag = TAG, msg = "apply local ad config failed", throwable = it) }

            configurationProvider.refreshRemote()
                .onSuccess { changed ->
                    if (changed) {
                        runCatching { applyAdConfig() }
                            .onFailure { LogUtils.w(tag = TAG, msg = "apply remote ad config failed", throwable = it) }
                    }
                }
                .onFailure { LogUtils.w(tag = TAG, msg = "refresh remote ad config failed", throwable = it) }
        }
    }

    private suspend fun applyAdConfig() {
        withContext(Dispatchers.Default) {
            applyMux.withLock {
                val r = configurationProvider.get()
                if (r.isSuccess) {
                    val wrap = r.getOrThrow()
                    if (appliedVersion == wrap.version) {
                        LogUtils.d(tag = TAG, msg = "already applied config: ${wrap.version}")
                        return@withLock
                    }
                    Adm.setConfiguration(wrap.cfg, object : AdRegistrationCallback {
                        override fun onSuccess(placementName: String) {
                            LogUtils.d(tag = TAG, msg = "ad platform ready: $placementName")
                        }

                        override fun onFailure(placementName: String, error: AdErrorC) {
                            LogUtils.w(tag = TAG, msg = "ad platform failed: $placementName, ${error.message}")
                        }
                    })
                    appliedVersion = wrap.version
                    appliedCount.update { it + 1 }
                    LogUtils.d(tag = TAG, msg = "applied config: ${wrap.version}")
                } else {
                    LogUtils.w(tag = TAG, msg = "apply ad config failed: ${r.exceptionOrNull()}")
                }
            }
        }
    }

    fun getAppliedCountFlow(): Flow<Int> = appliedCount

    suspend fun awaitConfiguration(timeoutMillis: Long): Boolean {
        if (appliedCount.value > 0) {
            return true
        }
        return withTimeoutOrNull(timeoutMillis) {
            appliedCount.first { it > 0 }
            true
        } ?: false
    }

}
