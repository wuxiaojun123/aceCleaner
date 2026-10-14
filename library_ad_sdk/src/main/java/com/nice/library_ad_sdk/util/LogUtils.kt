package com.nice.library_ad_sdk.util

import android.util.Log
import com.nice.library_ad_sdk.BuildConfig

/** 所有模块共用的日志输出工具，通过 DEBUG 控制是否输出。 */
object LogUtils {

    private const val DEFAULT_TAG = "CleanBox"

    @Volatile
    var DEBUG = BuildConfig.DEBUG

    fun d(msg: String, tag: String = DEFAULT_TAG) {
        if (DEBUG) Log.d(tag, msg)
    }

    fun i(msg: String, tag: String = DEFAULT_TAG) {
        if (DEBUG) Log.i(tag, msg)
    }

    fun w(msg: String, tag: String = DEFAULT_TAG, throwable: Throwable? = null) {
        if (DEBUG) Log.w(tag, msg, throwable)
    }

    fun e(msg: String, tag: String = DEFAULT_TAG, throwable: Throwable? = null) {
        if (DEBUG) Log.e(tag, msg, throwable)
    }
}
