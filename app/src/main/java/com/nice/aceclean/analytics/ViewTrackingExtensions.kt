package com.nice.aceclean.analytics

import android.view.View
import com.nice.aceclean.util.EventUtils

/**
 * 用统一埋点替代普通点击监听：先上报，再执行原有业务逻辑。
 * 不增加防抖或拦截触摸，保留点击、键盘和无障碍触发行为。
 * canHandleClick 用于原有的业务交互锁，返回 false 时不执行埋点或业务回调。
 * 同一个 View 后续重新设置点击监听时，会按 Android 原有规则覆盖本次监听。
 */
fun View.setTrackedClickListener(
    eventName: String,
    canHandleClick: () -> Boolean = { true },
    onClick: (View) -> Unit = {},
) {
    setOnClickListener { view ->
        if (!canHandleClick()) return@setOnClickListener
        EventUtils.track(eventName)
        onClick(view)
    }
}
