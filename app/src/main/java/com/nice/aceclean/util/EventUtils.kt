package com.nice.aceclean.util

import com.cater.bi.e.Eve

/**
 * 埋点事件统一入口。点击事件按页面与操作命名；新增事件请在对应分组添加常量及中文说明。
 * 事件名固定，不依赖控件 ID、翻译文案、列表位置或资源混淆结果；不附加页面或文件参数。
 * Eve 仍会按原有拦截器补充设备公共属性。
 */
object EventUtils {

    /** 页面创建事件：例如 show_MainActivity；Activity 类名由现有混淆规则保留。 */
    fun activityShowEvent(activityClass: Class<*>): String = "show_${activityClass.simpleName}"

    /** 统一上报，埋点异常不影响原有点击行为；点击次数不在这里防抖或去重。 */
    fun track(eventName: String) {
        runCatching { Eve.send(eventName, emptyMap()) }
            .onFailure { LogUtils.e(tag = "EventUtils", msg = "Failed to report $eventName", throwable = it) }
    }

    // 首页

    /** 首页：点击立即清理。 */
    const val CLICK_HOME_CLEANUP = "click_home_cleanup"

    /** 首页：点击网络流量卡片。 */
    const val CLICK_HOME_NETWORK = "click_home_network"

    /** 首页：点击通知清理功能卡片。 */
    const val CLICK_HOME_NOTIFICATIONS = "click_home_notifications"

    /** 首页：点击应用管理卡片。 */
    const val CLICK_HOME_APPS = "click_home_apps"

    /** 首页：点击截图清理卡片。 */
    const val CLICK_HOME_SCREENSHOTS = "click_home_screenshots"

    /** 首页：点击大文件清理卡片。 */
    const val CLICK_HOME_LARGE_FILES = "click_home_large_files"

    /** 首页：点击视频清理卡片。 */
    const val CLICK_HOME_VIDEOS = "click_home_videos"

    /** 首页：点击相似照片清理卡片。 */
    const val CLICK_HOME_DUPLICATES = "click_home_duplicates"

    /** 首页：点击内存状态卡片。 */
    const val CLICK_HOME_RAM = "click_home_ram"

    /** 首页：点击设备检测卡片。 */
    const val CLICK_HOME_DEVICE = "click_home_device"

    /** 首页：点击电池信息卡片。 */
    const val CLICK_HOME_BATTERY = "click_home_battery"

    /** 首页：点击网络测速卡片。 */
    const val CLICK_HOME_SPEED_TEST = "click_home_speed_test"

    /** 首页：点击顶部语言入口。 */
    const val CLICK_HOME_LANGUAGE = "click_home_language"

    /** 首页：点击固定标题栏语言入口。 */
    const val CLICK_HOME_PINNED_LANGUAGE = "click_home_pinned_language"

    /** 首页：点击应用设置入口。 */
    const val CLICK_HOME_SETTINGS = "click_home_settings"

    /** 首页：点击通知权限提示卡片。 */
    const val CLICK_HOME_NOTIFICATION_TIP = "click_home_notification_tip"

    /** 首页：点击通知权限提示开关。 */
    const val CLICK_HOME_NOTIFICATION_SWITCH = "click_home_notification_switch"

    // 垃圾清理结果

    /** 垃圾清理结果：点击返回。 */
    const val CLICK_CLEANUP_BACK = "click_cleanup_back"

    /** 垃圾清理结果：点击更多清理。 */
    const val CLICK_CLEANUP_MORE = "click_cleanup_more"

    /** 垃圾清理结果：点击移除。 */
    const val CLICK_CLEANUP_REMOVE = "click_cleanup_remove"

    /** 垃圾清理结果：点击重新扫描。 */
    const val CLICK_CLEANUP_RESCAN = "click_cleanup_rescan"

    /** 垃圾清理结果：点击查找大文件。 */
    const val CLICK_CLEANUP_LARGE_FILES = "click_cleanup_large_files"

    /** 垃圾清理结果：点击系统缓存清理。 */
    const val CLICK_CLEANUP_SYSTEM_CACHE = "click_cleanup_system_cache"

    /** 垃圾清理结果：点击分类勾选行。 */
    const val CLICK_CLEANUP_CATEGORY = "click_cleanup_category"

    // 语言设置

    /** 语言设置：点击返回。 */
    const val CLICK_LANGUAGE_BACK = "click_language_back"

    /** 语言设置：点击语言条目。 */
    const val CLICK_LANGUAGE_SELECT = "click_language_select"

    // 设备扫描

    /** 设备扫描：点击开始使用。 */
    const val CLICK_DEVICE_SCAN_START = "click_device_scan_start"

    // 设备报告

    /** 设备报告：点击确认。 */
    const val CLICK_DEVICE_REPORT_OK = "click_device_report_ok"

    /** 设备报告：点击标题栏返回。 */
    const val CLICK_DEVICE_REPORT_BACK = "click_device_report_back"

    // 电池信息

    /** 电池信息：点击知道了。 */
    const val CLICK_BATTERY_GOT_IT = "click_battery_got_it"

    /** 电池信息：点击标题栏返回。 */
    const val CLICK_BATTERY_BACK = "click_battery_back"

    // 网络测速

    /** 网络测速：点击开始或重新测速。 */
    const val CLICK_SPEED_TEST_START = "click_speed_test_start"

    /** 网络测速：点击标题栏返回。 */
    const val CLICK_SPEED_TEST_BACK = "click_speed_test_back"

    // 网络流量

    /** 网络流量：点击返回。 */
    const val CLICK_NETWORK_BACK = "click_network_back"

    // 通知白名单

    /** 通知白名单：点击返回。 */
    const val CLICK_ALLOWED_APPS_BACK = "click_allowed_apps_back"

    /** 通知白名单：点击搜索输入框。 */
    const val CLICK_ALLOWED_APPS_SEARCH = "click_allowed_apps_search"

    /** 通知白名单：点击应用行切换白名单状态。 */
    const val CLICK_ALLOWED_APPS_ROW = "click_allowed_apps_row"

    /** 通知白名单：点击应用白名单开关。 */
    const val CLICK_ALLOWED_APPS_SWITCH = "click_allowed_apps_switch"

    // 内存状态

    /** 内存状态：点击授权使用情况访问。 */
    const val CLICK_RAM_ALLOW = "click_ram_allow"

    /** 内存状态：点击确认。 */
    const val CLICK_RAM_OK = "click_ram_ok"

    /** 内存状态：点击应用关闭按钮。 */
    const val CLICK_RAM_CLOSE_APP = "click_ram_close_app"

    /** 内存状态：点击标题栏返回。 */
    const val CLICK_RAM_BACK = "click_ram_back"

    // 应用管理

    /** 应用管理：点击返回。 */
    const val CLICK_APPS_BACK = "click_apps_back"

    /** 应用管理：点击卸载选中应用。 */
    const val CLICK_APPS_UNINSTALL = "click_apps_uninstall"

    /** 应用管理：点击应用条目切换选择。 */
    const val CLICK_APPS_SELECT = "click_apps_select"

    /** 应用管理：点击名称排序标题，包含切换排序方向。 */
    const val CLICK_APPS_SORT_NAME = "click_apps_sort_name"

    /** 应用管理：点击安装时间排序标题，包含切换排序方向。 */
    const val CLICK_APPS_SORT_INSTALLED = "click_apps_sort_installed"

    /** 应用管理：点击大小排序标题，包含切换排序方向。 */
    const val CLICK_APPS_SORT_SIZE = "click_apps_sort_size"

    /** 应用管理：点击最近使用时间排序标题，包含切换排序方向。 */
    const val CLICK_APPS_SORT_LAST_USED = "click_apps_sort_last_used"

    // 通知清理

    /** 通知清理：点击顶部返回。 */
    const val CLICK_NOTIFICATIONS_BACK = "click_notifications_back"

    /** 通知清理：点击固定标题栏返回。 */
    const val CLICK_NOTIFICATIONS_PINNED_BACK = "click_notifications_pinned_back"

    /** 通知清理：点击白名单入口。 */
    const val CLICK_NOTIFICATIONS_WHITELIST = "click_notifications_whitelist"

    /** 通知清理：点击清理全部。 */
    const val CLICK_NOTIFICATIONS_CLEAR = "click_notifications_clear"

    /** 通知清理：点击应用条目打开系统通知设置。 */
    const val CLICK_NOTIFICATIONS_APP = "click_notifications_app"

    /** 通知清理：点击通知拦截开关。 */
    const val CLICK_NOTIFICATIONS_SWITCH = "click_notifications_switch"

    // 删除成功

    /** 删除成功：点击返回首页。 */
    const val CLICK_DELETE_SUCCESS_BACK = "click_delete_success_back"

    /** 删除成功：点击立即体验推荐功能。 */
    const val CLICK_DELETE_SUCCESS_TRY = "click_delete_success_try"

    /** 删除成功：点击下方推荐功能条目。 */
    const val CLICK_DELETE_SUCCESS_FEATURE = "click_delete_success_feature"

    // 相似照片清理

    /** 相似照片清理：点击返回。 */
    const val CLICK_DUPLICATES_BACK = "click_duplicates_back"

    /** 相似照片清理：点击删除选中照片。 */
    const val CLICK_DUPLICATES_DELETE = "click_duplicates_delete"

    /** 相似照片清理：点击保留最佳照片开关。 */
    const val CLICK_DUPLICATES_KEEP_BEST = "click_duplicates_keep_best"

    /** 相似照片清理：点击弹窗确认删除。 */
    const val CLICK_DUPLICATES_CONFIRM_DELETE = "click_duplicates_confirm_delete"

    /** 相似照片清理：点击弹窗取消删除。 */
    const val CLICK_DUPLICATES_CANCEL_DELETE = "click_duplicates_cancel_delete"

    // 大文件清理

    /** 大文件清理：点击返回。 */
    const val CLICK_LARGE_FILES_BACK = "click_large_files_back"

    /** 大文件清理：点击文件类型筛选入口。 */
    const val CLICK_LARGE_FILES_TYPE = "click_large_files_type"

    /** 大文件清理：点击文件大小筛选入口。 */
    const val CLICK_LARGE_FILES_SIZE = "click_large_files_size"

    /** 大文件清理：点击文件时间筛选入口。 */
    const val CLICK_LARGE_FILES_TIME = "click_large_files_time"

    /** 大文件清理：点击清理选中文件。 */
    const val CLICK_LARGE_FILES_CLEAN = "click_large_files_clean"

    /** 大文件清理：点击筛选条件选项。 */
    const val CLICK_LARGE_FILES_FILTER_OPTION = "click_large_files_filter_option"

    /** 大文件清理：点击文件条目切换选择。 */
    const val CLICK_LARGE_FILES_SELECT = "click_large_files_select"

    /** 大文件清理：点击弹窗确认删除。 */
    const val CLICK_LARGE_FILES_CONFIRM_DELETE = "click_large_files_confirm_delete"

    /** 大文件清理：点击弹窗取消删除。 */
    const val CLICK_LARGE_FILES_CANCEL_DELETE = "click_large_files_cancel_delete"

    // 大文件筛选弹窗

    /** 大文件筛选弹窗：点击类型页签。 */
    const val CLICK_LARGE_FILES_POPUP_TYPE = "click_large_files_popup_type"

    /** 大文件筛选弹窗：点击大小页签。 */
    const val CLICK_LARGE_FILES_POPUP_SIZE = "click_large_files_popup_size"

    /** 大文件筛选弹窗：点击时间页签。 */
    const val CLICK_LARGE_FILES_POPUP_TIME = "click_large_files_popup_time"

    /** 大文件筛选弹窗：点击空白区域关闭。 */
    const val CLICK_LARGE_FILES_POPUP_CLOSE = "click_large_files_popup_close"

    // 截图清理

    /** 截图清理：点击返回。 */
    const val CLICK_SCREENSHOTS_BACK = "click_screenshots_back"

    /** 截图清理：点击截图页签。 */
    const val CLICK_SCREENSHOTS_TAB = "click_screenshots_tab"

    /** 截图清理：点击其他图片页签。 */
    const val CLICK_SCREENSHOTS_OTHERS_TAB = "click_screenshots_others_tab"

    /** 截图清理：点击全选。 */
    const val CLICK_SCREENSHOTS_SELECT_ALL = "click_screenshots_select_all"

    /** 截图清理：点击清理。 */
    const val CLICK_SCREENSHOTS_CLEAN = "click_screenshots_clean"

    /** 截图清理：点击月份分组全选。 */
    const val CLICK_SCREENSHOTS_GROUP_SELECT = "click_screenshots_group_select"

    /** 截图清理：点击图片条目切换选择。 */
    const val CLICK_SCREENSHOTS_SELECT = "click_screenshots_select"

    /** 截图清理：点击弹窗确认删除。 */
    const val CLICK_SCREENSHOTS_CONFIRM_DELETE = "click_screenshots_confirm_delete"

    /** 截图清理：点击弹窗取消删除。 */
    const val CLICK_SCREENSHOTS_CANCEL_DELETE = "click_screenshots_cancel_delete"

    // 视频清理

    /** 视频清理：点击返回。 */
    const val CLICK_VIDEOS_BACK = "click_videos_back"

    /** 视频清理：点击全选。 */
    const val CLICK_VIDEOS_SELECT_ALL = "click_videos_select_all"

    /** 视频清理：点击清理。 */
    const val CLICK_VIDEOS_CLEAN = "click_videos_clean"

    /** 视频清理：点击月份分组全选。 */
    const val CLICK_VIDEOS_GROUP_SELECT = "click_videos_group_select"

    /** 视频清理：点击视频条目切换选择。 */
    const val CLICK_VIDEOS_SELECT = "click_videos_select"

    /** 视频清理：点击弹窗确认删除。 */
    const val CLICK_VIDEOS_CONFIRM_DELETE = "click_videos_confirm_delete"

    /** 视频清理：点击弹窗取消删除。 */
    const val CLICK_VIDEOS_CANCEL_DELETE = "click_videos_cancel_delete"

    // 新手电池信息

    /** 新手电池信息：点击立即检查。 */
    const val CLICK_FIRST_BATTERY_CHECK = "click_first_battery_check"

    // 新手垃圾扫描结果

    /** 新手垃圾扫描结果：点击开始清理。 */
    const val CLICK_FIRST_JUNK_CLEAN = "click_first_junk_clean"

    // 悬浮窗权限引导

    /** 悬浮窗权限引导：点击跳过。 */
    const val CLICK_OVERLAY_GUIDE_SKIP = "click_overlay_guide_skip"

    /** 悬浮窗权限引导：点击授权。 */
    const val CLICK_OVERLAY_GUIDE_ALLOW = "click_overlay_guide_allow"

    // 新手设备详情

    /** 新手设备详情：点击继续。 */
    const val CLICK_FIRST_DEVICE_CONTINUE = "click_first_device_continue"

    /** 新手设备详情：点击跳过。 */
    const val CLICK_FIRST_DEVICE_SKIP = "click_first_device_skip"

    // 新手存储权限弹窗

    /** 新手存储权限弹窗：点击关闭。 */
    const val CLICK_FIRST_STORAGE_CLOSE = "click_first_storage_close"

    /** 新手存储权限弹窗：点击允许访问。 */
    const val CLICK_FIRST_STORAGE_ALLOW = "click_first_storage_allow"

    // 新手完成页

    /** 新手完成页：点击返回。 */
    const val CLICK_FIRST_COMPLETE_BACK = "click_first_complete_back"

    /** 新手完成页：点击内存管理卡片或按钮。 */
    const val CLICK_FIRST_COMPLETE_RAM = "click_first_complete_ram"

    /** 新手完成页：点击通知清理卡片或按钮。 */
    const val CLICK_FIRST_COMPLETE_NOTIFICATIONS = "click_first_complete_notifications"

    /** 新手完成页：点击电池信息卡片或按钮。 */
    const val CLICK_FIRST_COMPLETE_BATTERY = "click_first_complete_battery"

    // 新手通知权限弹窗

    /** 新手通知权限弹窗：点击关闭。 */
    const val CLICK_FIRST_NOTIFICATION_CLOSE = "click_first_notification_close"

    /** 新手通知权限弹窗：点击授予权限。 */
    const val CLICK_FIRST_NOTIFICATION_ALLOW = "click_first_notification_allow"

    // 新手悬浮窗权限弹窗

    /** 新手悬浮窗权限弹窗：点击暂不设置。 */
    const val CLICK_FIRST_OVERLAY_LATER = "click_first_overlay_later"

    /** 新手悬浮窗权限弹窗：点击立即设置。 */
    const val CLICK_FIRST_OVERLAY_FIX = "click_first_overlay_fix"

    // 新手垃圾结果列表

    /** 新手垃圾结果列表：点击分类展开或收起。 */
    const val CLICK_FIRST_JUNK_EXPAND = "click_first_junk_expand"

    /** 新手垃圾结果列表：点击分类全选。 */
    const val CLICK_FIRST_JUNK_CATEGORY = "click_first_junk_category"

    /** 新手垃圾结果列表：点击文件条目切换选择。 */
    const val CLICK_FIRST_JUNK_ITEM = "click_first_junk_item"

    // 相似照片列表

    /** 相似照片列表：点击未选中标记选择照片。 */
    const val CLICK_DUPLICATES_SELECT = "click_duplicates_select"

    /** 相似照片列表：点击已选中标记取消选择。 */
    const val CLICK_DUPLICATES_DESELECT = "click_duplicates_deselect"

    /** 相似照片列表：点击缩略图预览照片。 */
    const val CLICK_DUPLICATES_PREVIEW = "click_duplicates_preview"

    const val CLICK_PHOTO_VIEWER_BACK = "click_photo_viewer_back"

    // 快捷清理引导弹窗

    /** 快捷清理引导弹窗：点击添加快捷开关。 */
    const val CLICK_QUICK_TILE_ADD = "click_quick_tile_add"

    /** 快捷清理引导弹窗：点击暂不添加。 */
    const val CLICK_QUICK_TILE_LATER = "click_quick_tile_later"

    // 通用删除确认弹窗

    /** 通用删除确认弹窗：点击确认，业务页面可以传入专属事件。 */
    const val CLICK_DELETE_DIALOG_CONFIRM = "click_delete_dialog_confirm"

    /** 通用删除确认弹窗：点击取消，业务页面可以传入专属事件。 */
    const val CLICK_DELETE_DIALOG_CANCEL = "click_delete_dialog_cancel"

    // 通用操作面板

    /** 通用操作面板：点击操作项，业务页面可以传入专属事件。 */
    const val CLICK_ACTION_SHEET_ACTION = "click_action_sheet_action"

    /** 通用操作面板：点击取消，业务页面可以传入专属事件。 */
    const val CLICK_ACTION_SHEET_CANCEL = "click_action_sheet_cancel"

    // 网络流量权限引导

    /** 网络流量权限引导：点击返回。 */
    const val CLICK_NETWORK_PERMISSION_BACK = "click_network_permission_back"

    /** 网络流量权限引导：点击授权。 */
    const val CLICK_NETWORK_PERMISSION_ALLOW = "click_network_permission_allow"

    // 通知清理权限引导

    /** 通知清理权限引导：点击返回。 */
    const val CLICK_NOTIFICATIONS_PERMISSION_BACK = "click_notifications_permission_back"

    /** 通知清理权限引导：点击授权。 */
    const val CLICK_NOTIFICATIONS_PERMISSION_ALLOW = "click_notifications_permission_allow"

    // 应用管理权限引导

    /** 应用管理权限引导：点击返回。 */
    const val CLICK_APPS_PERMISSION_BACK = "click_apps_permission_back"

    /** 应用管理权限引导：点击授权。 */
    const val CLICK_APPS_PERMISSION_ALLOW = "click_apps_permission_allow"

    // 垃圾清理权限弹窗

    /** 垃圾清理权限弹窗：点击重试授权。 */
    const val CLICK_CLEANUP_PERMISSION_RETRY = "click_cleanup_permission_retry"

    /** 垃圾清理权限弹窗：点击应用设置。 */
    const val CLICK_CLEANUP_PERMISSION_SETTINGS = "click_cleanup_permission_settings"

    /** 垃圾清理权限弹窗：点击取消。 */
    const val CLICK_CLEANUP_PERMISSION_CANCEL = "click_cleanup_permission_cancel"

    // 垃圾清理确认弹窗

    /** 垃圾清理确认弹窗：点击确认清理。 */
    const val CLICK_CLEANUP_CONFIRM_DELETE = "click_cleanup_confirm_delete"

    /** 垃圾清理确认弹窗：点击取消清理。 */
    const val CLICK_CLEANUP_CANCEL_DELETE = "click_cleanup_cancel_delete"

    // 内存管理强制停止提示

    /** 内存管理强制停止提示：点击继续打开应用设置。 */
    const val CLICK_RAM_FORCE_STOP_CONTINUE = "click_ram_force_stop_continue"

    /** 内存管理强制停止提示：点击取消。 */
    const val CLICK_RAM_FORCE_STOP_CANCEL = "click_ram_force_stop_cancel"

    // 截图清理权限弹窗

    /** 截图清理权限弹窗：点击重试授权。 */
    const val CLICK_SCREENSHOTS_PERMISSION_RETRY = "click_screenshots_permission_retry"

    /** 截图清理权限弹窗：点击打开应用设置。 */
    const val CLICK_SCREENSHOTS_PERMISSION_SETTINGS = "click_screenshots_permission_settings"

    /** 截图清理权限弹窗：点击取消。 */
    const val CLICK_SCREENSHOTS_PERMISSION_CANCEL = "click_screenshots_permission_cancel"

    // 大文件清理权限弹窗

    /** 大文件清理权限弹窗：点击重试授权。 */
    const val CLICK_LARGE_FILES_PERMISSION_RETRY = "click_large_files_permission_retry"

    /** 大文件清理权限弹窗：点击打开应用设置。 */
    const val CLICK_LARGE_FILES_PERMISSION_SETTINGS = "click_large_files_permission_settings"

    /** 大文件清理权限弹窗：点击取消。 */
    const val CLICK_LARGE_FILES_PERMISSION_CANCEL = "click_large_files_permission_cancel"

    // 视频清理权限弹窗

    /** 视频清理权限弹窗：点击重试授权。 */
    const val CLICK_VIDEOS_PERMISSION_RETRY = "click_videos_permission_retry"

    /** 视频清理权限弹窗：点击打开应用设置。 */
    const val CLICK_VIDEOS_PERMISSION_SETTINGS = "click_videos_permission_settings"

    /** 视频清理权限弹窗：点击取消。 */
    const val CLICK_VIDEOS_PERMISSION_CANCEL = "click_videos_permission_cancel"
}
