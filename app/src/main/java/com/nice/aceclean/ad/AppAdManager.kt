package com.nice.aceclean.ad

import android.app.Application
import androidx.fragment.app.FragmentActivity
import com.nice.aceclean.BuildConfig
import com.nice.library_ad_sdk.AdSdkManager

/** App-specific placement names and entry point for the shared ad SDK. */
object AppAdManager {
    // Constants use placement IDs from spaces.json; AdMob unit IDs remain in that configuration.

    /** 清理原生1，用于新手完成引导页。 */
    const val NATIVE_1 = "5001"

    /** 清理原生2，尚未绑定业务场景。 */
    const val NATIVE_2 = "5002"

    /** 清理原生3，尚未绑定业务场景。 */
    const val NATIVE_3 = "5003"

    /** 清理插屏1，已用于冷启动。 */
    const val INTERSTITIAL_1 = "3001"

    /** 清理插屏2，已用于 HotStartManager 触发的热启动。 */
    const val INTERSTITIAL_2 = "3002"

    /** 清理插屏3，用于新手设备扫描完成。 */
    const val INTERSTITIAL_3 = "3003"

    /** 清理插屏4，用于新手垃圾扫描完成／电池详情。 */
    const val INTERSTITIAL_4 = "3004"

    /** 清理插屏5，用于新手清理完成／电池检查完成。 */
    const val INTERSTITIAL_5 = "3005"

    /** 清理插屏6，用于新手设备详情跳过。 */
    const val INTERSTITIAL_6 = "3006"

    /** 清理插屏7，用于首次开屏。 */
    const val INTERSTITIAL_7 = "3007"

    /** 清理插屏8，尚未绑定业务场景。 */
    const val INTERSTITIAL_8 = "3008"

    /** 清理插屏9，尚未绑定业务场景。 */
    const val INTERSTITIAL_9 = "3009"

    /** 清理插屏10，尚未绑定业务场景。 */
    const val INTERSTITIAL_10 = "3010"

    /** 清理插屏11，尚未绑定业务场景。 */
    const val INTERSTITIAL_11 = "3011"

    /** 清理插屏12，尚未绑定业务场景。 */
    const val INTERSTITIAL_12 = "3012"

    const val COLD_START = INTERSTITIAL_1
    const val HOT_START = INTERSTITIAL_2
    // New-user scenes use this app's configured AdMob placements in spaces.json.
    const val ONBOARDING_DEVICE_SCAN = INTERSTITIAL_3
    const val ONBOARDING_ANALYSIS = INTERSTITIAL_4
    const val ONBOARDING_FINISH = INTERSTITIAL_5
    const val ONBOARDING_SKIP = INTERSTITIAL_6
    const val FIRST_START = INTERSTITIAL_7
    const val ONBOARDING_NATIVE = NATIVE_1
    const val LOAD_TIMEOUT_MS = 15_000L

    const val splashPlacementsConfigured = true

    fun initialize(application: Application) {
        AdSdkManager.initialize(
            context = application,
            remoteConfigKey = null,
            debug = BuildConfig.DEBUG
        )
    }

    suspend fun preload(activity: FragmentActivity, placement: String): Boolean =
        AdSdkManager.preload(activity, placement, LOAD_TIMEOUT_MS)

    suspend fun showLoaded(activity: FragmentActivity, placement: String): Boolean =
        AdSdkManager.showLoaded(activity, placement)
}
