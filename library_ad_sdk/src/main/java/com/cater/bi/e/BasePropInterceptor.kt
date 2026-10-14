package com.cater.bi.e

import android.os.Build

/**
 * Device base information
 *
 * created on 2025/8/31
 * @author holmes
 */
class BasePropInterceptor: EvInterceptor {

    override fun onSend(
        ev: String,
        prop: MutableMap<String, Any>
    ): Result<Boolean> {
        prop.apply {
            put("model", Build.MODEL)
            put("brand", Build.BRAND)
            put("os_ver", Build.VERSION.SDK_INT)
        }
        return Result.success(true)
    }

}