package com.nice.aceclean.util

import com.nice.library_ad_sdk.util.LogUtils as SharedLogUtils


/**
 * 统一日志工具类
 * 通过 DEBUG 开关控制是否输出日志，发布时设为 false 即可关闭所有日志
 */
object LogUtils {

    private const val DEFAULT_TAG = "CleanBox"

    /** 总开关，release 时设为 false */
    var DEBUG: Boolean
        get() = SharedLogUtils.DEBUG
        set(value) {
            SharedLogUtils.DEBUG = value
        }

    fun d(msg: String, tag: String = DEFAULT_TAG) {
        SharedLogUtils.d(msg, tag)
    }

    fun i(msg: String, tag: String = DEFAULT_TAG) {
        SharedLogUtils.i(msg, tag)
    }

    fun w(msg: String, tag: String = DEFAULT_TAG, throwable: Throwable? = null) {
        SharedLogUtils.w(msg, tag, throwable)
    }

    fun e(msg: String, tag: String = DEFAULT_TAG, throwable: Throwable? = null) {
        SharedLogUtils.e(msg, tag, throwable)
    }
}
