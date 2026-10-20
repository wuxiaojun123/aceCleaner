# Push Library 接入说明

这套实现参考 `notify-* 2.2.0.261008.2` 的本地通知机制，负责场景监听、配置解析、内容缓存、展示和点击。日历、天气、新闻已有各自的折叠、普通、展开和悬浮布局。后台地址与鉴权由接入项目提供。

## 包结构

| 包 | 职责 |
| --- | --- |
| `api` | `PushLibrary` 入口、触发事件、宿主路由和埋点接口 |
| `backend` | 从自有后台获取配置的接口及 HTTPS 实现 |
| `config` | 场景、模板、JSON 解析 |
| `triggers` | 解锁、切后台、安装、Wi-Fi、充电/电量、定时触发 |
| `business.file` | MediaStore 文档和截图监听、文件通知内容 |
| `business.news` / `web` / `video` / `social` | 各业务类型的通知内容映射 |
| `business.stay` | 宿主显式启动的常驻前台通知 |
| `display` | 系统通知、悬浮视图、图片加载和点击入口 |
| `firebase` | FCM token 与配置刷新信号 |

## 宿主初始化

在 `Application.onCreate()` 中注入自己的配置接口和页面路由：

```kotlin
PushLibrary.initialize(
    application = this,
    source = HttpPushConfigSource("https://your-domain.example/api/push/config") {
        mapOf("Authorization" to "Bearer ${getYourToken()}")
    },
    router = PushRouter { context, click ->
        // 根据 click.target 打开宿主自己的页面。
    },
    tracker = PushTracker { event, attributes ->
        // 上报到宿主自己的统计接口。
    },
    onToken = { token ->
        // 可选：把 FCM token 上传到自己的后台。
    },
    topic = null, // 如需订阅 topic，传入后台约定的名称。
)
```

宿主可调用 `PushLibrary.refresh()` 主动更新配置，或 `PushLibrary.triggerAppScene(subType)` 触发 `app` 场景。配置会缓存最后一次成功解析的版本；拉取失败时继续使用缓存。`source` 也可以是自定义实现，无需使用 `HttpPushConfigSource`。

## 配置格式

支持明文 JSON，也支持旧库使用的 Base64 JSON。下面只启用 `app` 场景；其余场景缺省为关闭。旧库的 `sts`、`cd`、`dcm`、`display_type`、`temps`、`tempId`、`wgt`、`content`、`btnText`、`imageList` 和 `subType` 字段仍可读取。自己的后台建议显式传 `type`、`title` 和 `target`。

```json
{
  "globalDailyLimit": 4,
  "globalIntervalMillis": 60000,
  "channelName": "Updates",
  "scenes": {
    "app": {
      "sts": 1,
      "cd": 3600,
      "dcm": 2,
      "display_type": 1,
      "temps": [{
        "tempId": 1,
        "type": "default",
        "wgt": 100,
        "title": "Document tools",
        "content": "Your PDF tools are ready",
        "btnText": "Open",
        "imageList": ["https://your-domain.example/push/banner.png"],
        "target": "pdf_home"
      }]
    }
  }
}
```

`display_type`：`0` 优先悬浮并可回退到系统通知；`1` 仅系统通知；`2` 仅悬浮。`cdt` 使用 `HH:mm-HH:mm:秒数`，多个时段以分号分隔，`-1` 表示该时段不展示。定时触发使用 WorkManager，实际最短周期为 15 分钟。

`file` 场景需要额外提供 `suffixes`（如 `["pdf", "docx"]`），并由宿主处理全文件访问授权。截图场景需要相应的媒体读取权限。文件与截图场景只有在获得权限后才注册监听。Library 不会替宿主弹权限引导页。

## FCM 行为

FCM `data` 消息中的 `type=refresh_config` 会调用 `PushLibrary.refresh()`。其他 data 消息会进入接收回调但不会自动生成通知；“服务端即时通知”的字段与频控规则仍需和后台定稿。FCM `notification` 消息在 App 后台可能由系统直接展示，行为与此 Library 创建的本地场景通知不同。

如果宿主已有自己的 `FirebaseMessagingService`，应统一由宿主 Service 接收，然后调用 `PushLibrary.onNewFirebaseToken(token)` 和 `PushLibrary.onFirebaseData(data)`，并从合并 Manifest 中移除本库的 Service，避免多个 Service 抢同一个消息入口。

## 当前边界

- 日历、天气、新闻使用独立布局；网页、视频、社交仍使用通用内容布局。没有绑定旧库的新闻 SDK、广告 SDK 和路由器。
- 本库提供可选的 `PushStay.start/stop()` 常驻通知，使用 `nice_push_stay` 渠道；AceClean 保留自己的 `StickyNotificationService`，当前接入不会另外启动本库的常驻通知。本库暂未实现参考产物的开机恢复 Job。
- 自有后台的最终 API、鉴权、内容列表和即时 FCM 协议尚未提供，因此没有写死接口路径或代填业务数据。

## 日历、天气、新闻（本次补齐）

| 业务 | type / 原库 tempId | 内容与点击 |
| --- | --- | --- |
| 日历 | `calendar` / `904` | 本地当天日期、星期、概览文案；不读取系统日程。默认点击进入宿主。 |
| 天气 | `weather` / `905` | 城市、温度、观测时间、正文，支持雨/雷暴/高温/晴/多云图标与配色。仅使用未过期的天气观测，不随机伪造天气。 |
| 新闻 | `news` / `910` | 封面、标题、摘要、来源、发布时间。无图片时使用本地占位图；成功展示后轮换下一条。默认路由可打开内置阅读页。 |

旧配置没有 `type` 时，解析器会根据上述 `tempId` 自动选择正确业务模板。显式提供 `type` 时以它为准。

业务模板可配置在任意已有场景的 `temps` 中；它们不是新增的系统事件。现有场景冷却、时段禁发、VIP/自然量及日上限仍然生效。`PushLibrary.triggerCalendar()`、`triggerWeather()`、`triggerNews()` 默认触发 `app` 场景，并只选择对应业务模板；也可传 `scene = "ul"` 等已启用场景。

宿主在初始化后提供内容：

```kotlin
// 将你自己接口的数据映射为 PushWeatherData / List<PushNewsItem>。
PushLibrary.setWeather(weatherFromYourApi)
PushLibrary.setNews(newsFromYourApi)

// 应用在后台且满足当前场景频控时才会显示。
PushLibrary.triggerWeather()
PushLibrary.triggerNews()
PushLibrary.triggerCalendar()
```

`PushWeatherData` 字段：`title`、`description`、`condition`（`RAIN / STORM / HOT / CLEAR / CLOUDY`）、`location`、`temperature`、`target`、`observedAtMillis`、`expiresAtMillis`。宿主对象默认有效期 3 小时；远端 JSON **必须**提供观测和过期时间，全部使用毫秒时间戳。过期、未来时间和空标题数据不会参与选取。

`PushNewsItem` 字段：`id`、`title`、`url`、`description`、`imageUrl`、`source`、`publishedAtMillis`、`expiresAtMillis`。文章 URL 必须是有效 HTTPS 地址。过期/尚未发布的新闻会跳过，重复 ID 合并。数据及轮换位置保存在本地；发布/过期时间为 0 分别表示未知发布时间/未指定过期时间。

也可在配置 JSON 顶层添加 `weather` 对象、`news` 数组，字段与上面一致（天气 condition 不区分大小写）。缺省这两个字段时保留宿主提供的缓存；`"weather": null`、`"news": []` 显式清空。显式使用 `PushEvent` 发布单条新闻时需同时提供标题和 HTTPS target，可在 metadata 中提供 `newsId`、`source`、`publishedAtMillis`、`expiresAtMillis`。

每条新闻的 URL 被写入自己的 PendingIntent，因此后来更新新闻缓存不会改变已有通知的点击目标。宿主传自定义 `PushRouter` 时需要自行处理 `click.type == "news"`，可调用 `PushNewsActivity.open(context, click.target)`。`autoCancel=false` 会保留系统通知；关闭按钮只关闭，不路由。`cx`、`overlayCx` 分别控制系统通知和悬浮窗的关闭按钮。

## AceClean 当前接入

`App.onCreate()` 调用 `BusinessPushManager.initialize()`，读取 `app/src/main/assets/push_config.json`。配置启用了解锁、定时与主动场景，全局每天最多 3 条、两次通知至少间隔 60 秒；解锁/定时各冷却 6 小时，主动场景冷却 1 小时。可直接修改该文件调整策略。

日历可以直接展示。天气与新闻的数据入口已完成，实际数据仍需通过自有后台或上面的宿主 API 提供；没有有效数据时跳过对应模板。当前不包含第三方天气/新闻接口或演示新闻。接入实际后台时，将 `BusinessPushManager` 中的本地 `PushConfigSource` 替换为实际 source 即可。

新闻默认进入阅读页，日历/天气默认经现有通知入口返回首页。通知入口会保留自己的跳转，避免被热启动广告覆盖；现有清理快捷通知不变。
