# com.gg.notify AAR 逻辑与接口

本文对应当前 PDFSign 项目。应用接入了 7 个 `com.gg.notify` AAR。通知场景、视频条目和网页地址来自 Track SDK 提供的配置，新闻列表默认走 Infohub，文件、截图和已安装应用则来自 Android 本地系统查询。

分析依据为本机 Gradle 缓存中的同版本源码、AAR 字节码，以及项目中现有的 Debug 生成文件。下文接口参数和响应字段来自客户端的构造及解析代码。本次采用静态分析，未请求生产服务或抓取真机流量。实际下发值和启用场景以运行时配置为准，其他构建版本的 URL 和混淆键以各自生成的 `MM.java` 为准。

- [应用依赖](../app/build.gradle#L147)
- [依赖版本定义](../modules/config.gradle#L489)
- [应用初始化](../app/src/main/java/com/nice/readerpdf/pdfeditor/sign/viewerpdf/App.kt#L29)
- [宿主配置实现](../app/src/main/java/com/nice/readerpdf/pdfeditor/sign/viewerpdf/push/notify/MyNotifyConfig.kt#L9)
- [Debug 生成的 MM.java](../app/build/generated/java/generateDebugParams/com/i/a/collect/MM.java)

## 1. 依赖和职责

| AAR | 版本 | 职责 |
| --- | --- | --- |
| `notify-sdk` | `2.3.0.261109.1` | SDK 入口、配置解析、场景监听、频控、系统通知和悬浮窗、点击分发、FCM |
| `notify-news` | `2.3.0.261109.3` | 新闻通知模板、新闻及分类缓存、文章详情 |
| `notify-video` | `2.3.0.261109.1` | 视频通知模板和视频 WebView 页面 |
| `notify-web` | `2.3.0.261109.1` | 网页通知模板、地址轮换和网页展示 |
| `notify-social` | `2.3.0.261109.1` | 已安装社交 App 的图标和点击跳转 |
| `notify-stay` | `2.3.0.261109.1` | 常驻通知、前台服务和服务恢复 |
| `notify-file` | `2.3.0.261109.1` | MediaStore 文件变化及截图监听 |
| `com.mc.win.mammoth:news` | `1.0.31` | 新闻源适配和 HTTP 实现 |

`notify-service` 出现在 `modules/config.gradle` 中，当前 App 的依赖列表未引入它。

## 2. 配置请求及响应

`App.onCreate()` 先调用 `TM.initAndProvider()`，再调用 `NotifySdk.init()`。Track SDK 的 `WbManager` 取得配置后更新 `ConfigManager.configMapDataFlow`，宿主通过 `MyNotifyConfig.getConfigFlowData()` 把该 `Map<String, String>` 交给通知 SDK。宿主还通过 `BstKnt.isOriginTag` 提供自然量判断，`isVip()` 固定返回 `false`，广告和埋点分别由 `MyINotifyAd`、`MyTracker` 对接。

### 2.1 `WbManager.reqConfig()`

现有 Debug 构建的请求是 `POST https://wno.bobolover.com/ship/yell`，请求体为 JSON。地址由 `MM.Wb.new_host`、`MM.Custom.vpath2`、`MM.Custom.path1` 拼出，实际 HTTP 由 `ConfigManager.post2()` 发起。

| 代码字段 | Debug JSON 键 | 值 |
| --- | --- | --- |
| `pid` | `bgtbeu` | 项目 ID |
| `apid` | `tpyukx` | App ID |
| `cha` | `rkzbwcf` | 渠道 |
| `fw` | `gzdyray` | 固定 `"4"` |
| `fw_ver` | `seqdku` | 固定 `"3"` |
| `ver` | `dqkmjk` | App 版本 |
| `install_time` | `gptwdp` | 安装时间 |
| `android_id` | `ltdhpjd` | 宿主设备 Android ID |
| `cfg_ver` | `bfocbwlf` | 当前缓存的配置版本 |
| `rules` | `gqmoflmrd` | Base64 编码的规则 JSON |

`rules` 解码后包含 `language`、`installed_hour`、`cold_start_request`、`network_type`、`sdk_ver`、`os_type`、`brand`、`model`、`current_time`、`gaid`、`attribution`、`front_active_sec`。其中 `gaid` 字段来自 `PhoneUtils.getOAID()`。宿主提供扩展配置时，还可加入 `risk_type` 与 `user_ecpm`，后者包含 `count`、`type`、`ecpm`。

响应外层读取 `code`、`message`、`data`。业务 `code == 204` 表示配置无更新。`data` 中读取配置版本 `bfocbwlf`、分组版本 `smpvzt` 和参数对象 `itpltvobx`，对应代码中的 `cfg_ver`、`group_ver`、`params`。参数对象写本地缓存并更新配置流，Track SDK 还可把可用的广告位配置合并到同一个 Map。Firebase Remote Config 的刷新标记变化可以安排重新拉取。

### 2.2 通知实际消费的配置键

| 代码名称 | Debug 下发键 | 解析及用途 |
| --- | --- | --- |
| `ol_notify_b64_config` | `zehpannnqk` | Base64 JSON 对象，场景、频控、模板和展示渠道 |
| `ol_notify_video_cfg` | `rztwkk` | Base64 JSON 数组，视频标题、封面和页面 URL |
| `ol_notify_web_cfg` | `tmhelo` | Base64 JSON 数组，网页 URL 和内容 ID |
| `ol_notify_home_return_cfg` | `tngyyuac` | Base64 JSON 对象，Home 返回 App 的条件 |
| `ol_notify_ab_delay` | `yplpjs` | 切后台后等待秒数 |
| `notify_all_cd` | `kwzfjcfk` | 全局展示间隔，单位毫秒，默认 `5000` |
| `notify_all_dcm` | `fuppvk` | 全局日上限，正数时生效 |
| `notify_sdk_file_suffix` | `mgkgbde` | 后缀 JSON 数组。缺省走内置列表，空数组关闭文件监听 |
| `notify_preload_time` | `vwuagou` | 展示前等待广告预加载的配置 |
| `notify_click_magn` | `sisgjj` | 历史点击表现对模板权重的影响 |

## 3. notify-sdk

`NotifySdk.init()` 通过 DRouter 发现并初始化子模块，注册 Activity 和进程生命周期，监听通知权限与配置流。`ScenesConfigParser` 解码配置，`TriggerManager` 根据各场景的开关注册或注销监听。场景缺失或解析失败时使用关闭状态。

### 3.1 场景及子类型

| 场景 | 类 | 触发条件 |
| --- | --- | --- |
| `ul` | `ScreenUnlockReceiver` | 解锁。综合系统广播、显示状态变化和轮询，短时间内去重 |
| `ta` | `ScheduledWorker` | WorkManager 定时任务，根据当前场景配置和上次展示状态调度下一次 |
| `ab` | `AppBackgroundObserver` | 切后台后延时处理。子类型 `0` 普通、`1` 广告点击、`2` 新手流程中断、`3` Home 键。Home 分支可先检查 `HomeReturnHelper` |
| `pkg` | `PackageChangeReceiver` | 系统包变化广播。子类型 `1` 安装、`2` 卸载 |
| `wifi` | `WifiChangeTrigger` | 子类型 `1` Wi-Fi 连接、`2` 断开，并做事件去重 |
| `rq` | `NotifySnapshot` | `notify-file` 识别到新截图，子类型 `0` |
| `dc` | `BatteryChangeTrigger` | 子类型 `1` 接入电源、`2` 移除电源。向下跨过 `levels` 阈值时，从子类型 `3` 起对应阈值在配置中的位置 |
| `app` | `NotifySdk.triggerAppScene()` | 宿主或子模块主动触发 |
| `file` | `NotifyFile` | 子类型 `1` 文件新增、`2` 文件删除 |

### 3.2 频控、模板和点击

1. **选渠道。** `display_type=0` 优先使用悬浮窗，缺少悬浮窗权限时尝试系统通知。`1` 仅系统通知，`2` 仅悬浮窗。无可用渠道时结束。锁屏会拦截悬浮窗。
2. **检查展示条件。** `NotificationHelper` 检查屏幕是否亮起、宿主是否在前台，以及全局间隔、全局日上限和场景频控。`sts` 是开关，`dly` 是按安装时间计算的首次延迟，`cd` 是场景间隔，`dcm` 是日上限。`org_dly` 给自然量追加条件，负值会拦截自然量。`cdt` 可按本地时间覆盖间隔，时段值 `-1` 表示禁用。悬浮窗可用 `overlay_cd`、`overlay_dly`、`overlay_dcm` 维护独立频控。
3. **选模板。** `NotificationTemplateSelector` 优先匹配 `subType`，按规则回退到通用模板，再筛选已注册、有数据的候选。结合 `wgt` 和历史点击表现做平滑加权轮询。同一 `tempId` 的实现优先级为宿主、子模块、SDK 内置。
4. **创建视图和点击事件。** `RemoteViewsFactory` 生成小、中、大和悬浮窗布局。模板参数可包括 `content`、`btnText`、`contentId`、`imageList`、`autoCancel`、`ongoing`、关闭按钮的显示规则和关闭概率。`NotifyDeeplinkActivity` 从 Intent 还原场景、模板、通知 ID、内容 ID、操作和目标数据，再处理关闭或跳转。
5. **重发和统计。** 系统通知在场景 `lt_sts=1` 且模板 `lt>0` 时可以重发。`NotificationResendCoordinator` 的执行间隔是 **3 秒**，源码中的部分注释使用了其他数值。展示、跳过、点击、关闭等埋点通过宿主 `MyTracker` 转发给 `EventManager`。

内置模板 `901–903` 为普通样式，`904` 为日历样式，`905` 为天气样式，`906` 为图片样式，`908` 为电话样式。天气模板没有独立天气数据接口。图片模板使用配置提供的 `imageList`，图片由 Glide 加载。

### 3.3 NotifyFirebaseService

初始化时订阅 `MM.Custom.remote_config` 指定的 Firebase Messaging topic，现有 Debug 值为 `xivfaetof`。

- `onNewToken()` 上报 `fcm_token_refresh`，参数为 `token_length`。
- `onMessageReceived()` 上报 `fcm_message_received`，参数为 `message_id` 和逗号分隔的 `data_keys`。
- 把消息数据交给宿主和调用本地通知展示的语句均被注释。收到 FCM 数据消息本身不会触发本地通知模板流程。

## 4. notify-news 和 Mammoth

`NewsNotifyModule` 注册三个新闻模板，`NewsManager.init()` 初始化 Mammoth，并设置 `DidProvider` 和 `TpcAdInfoProvider`。宿主未设置新闻源覆盖，当前默认源为 **Infohub，ID `1`**。`DidProvider` 首次生成随机 UUID，保存到 `mam:uid` 后复用。

### 4.1 Infohub 默认接口

`InfohubNewsClient.request()` 发送 `GET https://innews.infohubnews.app/ad/mixnews`。

| Query 参数 | 取值 |
| --- | --- |
| `lang`、`cty` | 设备 Locale 的语言和国家，转小写，取不到时回退 `en`、`us` |
| `limit` | `pageSize + 5`。普通新闻通知补货时为 `15`，取分类首条时为 `6` |
| `offset` | `(max(page, 1) - 1) × limit`。通知的上述两条路径都是 `0` |
| `cc` | 固定 `kinetic` |
| `c` | 分类 ID，空或未识别时回退 `trending` |
| `ts` | Unix 时间戳，单位秒 |
| `uuid` | 本地保存的 Mammoth UUID |
| `session` | 初始 `init`，后续复用响应里的非空 `session` |

`InfohubResponse` 顶层读取 `ads` 数组和 `session`。`ads[]` 每项解析 `language`、`type`、`content`、`link`、`title`、`description`、`images`、`additional`。`additional` 包含 `contentSourceDisplay`、`contentSourceLogo`、`publishedAt`。

`InfohubNewsAdapter` 去掉 `type` 以 `-sc` 结尾的条目，再进行下列映射。

| 响应字段 | 通知使用的 `News` 字段 |
| --- | --- |
| `title` | 标题 |
| `description`，缺省时使用 `content` | 摘要 |
| `link` | 文章 URL |
| `images` 第一项 | 封面 |
| `additional.contentSourceDisplay` | 来源名 |
| `additional.publishedAt` | 发布时间 |
| `additional.contentSourceLogo` | 响应模型会解析，通知业务模型未采用 |

`Mammoth.requestCategories()` 在 Infohub 下返回内置分类，无需先发分类列表请求。分类有 `trending`、`entertainment`、`sports`、`business`、`lifestyle`、`politics`、`tech`、`science`、`health`、`environment`、`travel`、`food`。各分类的新闻通过同一个 `/ad/mixnews` 接口读取。

### 4.2 新闻模板、缓存和详情页

| ID | 类 | 行为 |
| --- | --- | --- |
| `910` | `NewsTemplate` | 从 `NewsRepository` 消费一条新闻，展示标题、摘要、封面、来源、时间。无缓存时异步调用 `Mammoth.requestNewsFlow("", "", 0, 10)` |
| `911` | `NewsTemplate2` | 使用分类和每个分类首条新闻的内存快照，渲染多分类卡片 |
| `912` | `NewsTemplate3` | 使用同一分类快照，渲染分类文案、图标和背景等 |

`NewsRefreshScheduler` 将并发补货合并成一轮，单次超时 **5 秒**，两次尝试至少间隔 **2 分钟**。失败、超时和空结果也消耗本次间隔。成功的非空结果替换新闻队列并持久化。模板消费一条时保存游标，并预加载下一条封面。

`NewsCategoryRepository` 先取分类，再逐项调用 `Mammoth.requestNewsFlow("", category.id, 0, 1)` 获取第一条，整轮超时 **40 秒**。单个分类失败时其 `firstNews` 可为空，整轮失败或分类列表为空时保留旧快照。每读取 3 次分类快照会安排异步刷新。模板选择阶段使用已经可用的数据，缺失数据的异步拉取不会阻塞本次通知。

点击文章后，`NewsLaunchDataMapper` 还原 Intent 中的序列化 `News`，`NewsActivity` 把其中的 `url` 交给 `NewsWebView` 加载。新闻原生广告位为 `44000012`，由宿主广告接口负责。

### 4.3 AAR 内置的其他新闻源

以下是 Mammoth 打包的可选来源。当前工程的通知初始化使用上面的 Infohub 默认源。

| 来源 | 方法、接口及字段 |
| --- | --- |
| `0` Launcher365 | `POST https://news.launcher365.top/api/v1/news`。JSON 传 `country`、`category`、`page`、`page_size`，请求头带 `x-timestamp`、`x-auth-sign`、`x-package`、`x-deviceid`。响应读 `data[]` 中的 `title`、`description`、`publish_time`、`source_name`、`url`、`image`。该来源分类返回空列表 |
| `2` Max Browser | 基址 `https://mab-api.websearchapp.com`。`POST /api-v8/program/activate` 传 `session_type`，从 `code/msg/data` 中读取激活数据 `session_token`、`user_record`、`dp_force`、`dp_type`。分类请求为 `POST /api-v8/newsCenter/seekByCategoryV2`，传 `category_id`、分页 `token`，读 `data.records` 和 `data.token`。新闻记录含 `news_id`、`publish_time`、`news_link`、`news_caption`、`news_cover`、`video_url`、`content`、媒体 ID/名称/图标、`type`、阅读和点赞数、地区语言和分类 ID。SDK 也包含 `topNewsSeek`、`topNewsSeekV2` 和 `seekNewsDetail` |
| `3` Smart News | 基址 `https://www.smartnews.be/api/`。主链路包括 `POST v2/refresh` 和 `POST v3/items/channel/{categoryId}`。携带设备标识、地区语言、会话和 feed 分页信息。刷新模型读 `items`、`channelSelections`，内容层读 `channel`、`blocks`，再从 `links` 读取 `id`、`type`、`title`、`url`、`internalUrl`、`site`、`author`、`thumbnail`、`publishedTimestamp` 等 |

## 5. notify-video

`NotifyVideo` 注册 `VideoTemplate`，模板 ID 为 `920`。`VideoManager` 监听 `ol_notify_video_cfg`，解码 Base64 后解析 JSON 数组。`VideoConfigCache` 比较配置原文，仅在变更时重新解析。模块按配置顺序循环消费，保存下次索引，预加载下一项封面。

数组每项读取 `contentId`、`title`、`content`、`imageUrl`、`videoUrl`。`contentId` 解析失败用 `0`，配置整体解析失败得到空列表。封面图片由 Glide 按 `imageUrl` 加载。用户点击通知时，`VideoWebActivity` 取出目标数据，把 `videoUrl` 交给 WebView，并通过宿主广告接口处理广告。该模块没有独立视频列表接口。

## 6. notify-web

`NotifyWeb` 注册 `WebTemplate`，模板 ID 为 `950`。`WebDataManager` 监听 `ol_notify_web_cfg`，解 Base64 后读 JSON 数组。每项读取 `contentId` 和 `webUrl`，空白地址跳过，无法解析的 ID 用 `0`。链接按配置顺序轮换，索引持久化。通知标题、优惠图和按钮等可以来自 SDK 内置的八套本地卡片素材。

`WebTemplate` 把 URL 传给 `WebActivity`。后者负责页面加载、进度、失败状态、外部跳转及宿主广告展示。实际页面和资源由配置指定的网站返回。

## 7. notify-social

`NotifySocial` 注册 `SocialIconsTemplate`，模板 ID 为 `940`。模板使用 `PackageManager` 查询指定包的本机安装状态和图标，范围包括 WhatsApp、Facebook、Instagram、YouTube 和多个 TikTok 包。点击把目标包名传给 `SocialIconsAdActivity`，经宿主广告回调后尝试启动该包。数据来自系统包查询，模块没有社交列表接口或社交内容读取逻辑。

## 8. notify-stay

`NotifyStay` 初始化 `StayServiceManager`。`StayForegroundService` 通过 `startForeground()` 发布 ID 为 `930` 的常驻模板，并支持刷新通知的 `RemoteViews`。`StayRestartScheduler` 与 `StayRestartJobService` 在满足条件时通过 JobScheduler 尝试恢复前台服务。

宿主的 `CommonNotifyTemplate : StayTemplate` 继承同一个模板 ID，替换原布局并绑定 **打开 PDF、签名、合并、所有文件** 四个入口。常驻模块的数据来自本地模板、服务状态和宿主更新。该模块没有独立 HTTP 接口。 [宿主常驻卡片](../app/src/main/java/com/nice/readerpdf/pdfeditor/sign/viewerpdf/push/notify/CommonNotifyTemplate.kt#L17)

## 9. notify-file

### 9.1 file 包

`NotifyFile` 注册 `file` 场景。`FileChangeMonitor` 通过 MediaStore 建立基线，发生变动时按约 **800 毫秒**的窗口合并扫描，再对比出新增和删除。`FileChangeEventMapper` 把两种变化分别映射为子类型 `1`、`2`，每种事件携带一条可用路径。`FileChangeEventCooldown` 有 **5 秒**内置冷却，同一批扫描识别的后续事件也受该冷却限制。

默认后缀为 `.pdf`、`.xls`、`.xlsx`、`.xlsm`、`.csv`、`.doc`、`.docx`、`.docm`、`.dot`、`.apk`、`.txt`。线上配置可覆盖此列表。Android 11+ 要求所有文件访问权限，Android 10 要求旧式外部存储模式和读取权限，更早版本检查外部存储读取权限。

`FileTemplate` 的 ID 为 `907`，使用本次事件携带的路径快照决定标题和文件图标。主文件名超过 20 个 Unicode 码点时保留前 10 和后 2 个码点，扩展名照常展示。删除事件的路径用于记录原文件位置，目标文件可能已经不存在。此链路未发现上传文件或路径的 HTTP 调用。

### 9.2 snapshot 包

`NotifySnapshot` 注册 `rq` 场景。`MediaStoreScreenshotMonitor` 监听 MediaStore 图片索引，读取 URI、文件名、路径、新增时间和拍摄时间。`ScreenshotRecognitionPolicy` 检查最近约 **15 秒**的数据，并匹配 `screenshot`、`screen_shot`、`screen-shot`、`screen capture`、`screencap`、`截屏`、`截图` 等词。按 URI 去重后，发送 `subType=0` 的场景事件。

此实现要求 Android 11+ 和所有文件访问权限。此链路未发现上传截图或其元数据的 HTTP 调用。
