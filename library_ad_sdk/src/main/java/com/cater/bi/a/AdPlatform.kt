package com.cater.bi.a

/**
 * Ad platform configuration
 * 
 * created on 2025/8/10
 * @author holmes
 */
class AdPlatform(
    val name: String,
    val appid: String = "",
    val appkey: String = ""
) {
    companion object {
        const val ADMOB = "admob"
        const val TOPON = "topon"
        const val MOCK = "mock"
    }
}
