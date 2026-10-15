# 新手流程

流程最初参考 `cleanGo/cleanboxpro`，当前界面已按 `designs/onboarding-orange/` 的交互原型落地。页面统一使用暖白 `#FAF9F7`、白色卡片、橙色 `#FF6B2C` 与深灰文字。首页保持原有设计。启动页沿用原有 `ActivitySplashBinding`、`activity_splash.xml`、Logo、横向进度条与加载文案，仅统一背景和进度颜色。

## 当前视觉与动画

设备扫描、垃圾扫描、清理、电池扫描、电池详情、电池检查、完成页和备用悬浮窗引导均使用同一套橙色图标与动画。设备信息与垃圾结果页保留真实数据、分类展开、勾选及金额联动。下载／APK 仍默认不选，没有选中项时允许 Continue。

| Android 资源 | 对应原型动画 | 使用页面 |
| --- | --- | --- |
| `raw/onboarding_orange_device_scan.json` | `device-scan.json` | 设备扫描 |
| `raw/onboarding_orange_junk_scan.json` | `junk-scan.json` | 垃圾扫描 |
| `raw/onboarding_orange_junk_clean.json` | `junk-clean.json` | 清理、备用悬浮窗引导 |
| `raw/onboarding_orange_battery_scan.json` | `battery-scan.json` | 电池扫描、电池详情 |
| `raw/onboarding_orange_battery_check.json` | `battery-check.json` | 电池检查 |
| `raw/onboarding_orange_complete.json` | `complete.json` | 清理／电池检查完成 |

六个 JSON 与原型资源相同，均为透明背景、320×320、30 fps、90 帧的纯形状动画。前四个循环，电池检查与完成反馈播放一次后停留。页面通过 `BaseOnboardingActivity.trackOnboardingAnimation(binding.xxx)` 注册动画，进入前台恢复，进入后台暂停，销毁时释放。布局中的 `lottie_autoPlay` 关闭，避免页面被广告或系统设置遮盖时继续播放。

权限说明使用白色圆角底部弹窗，继续调用真实 Android 权限设置，不使用原型的模拟开关。完成动画显示约 1.6 秒后再出现通知访问说明；提前返回或进入推荐功能会取消这次延迟提示。主要按钮保留轻微扫光，移除了反复放大和透明度呼吸。

共用视觉样式位于 `values/styles_onboarding_orange.xml`，底部弹窗背景为 `drawable/onboarding_bg_orange_sheet.xml`，进度条复用 `drawable/onboarding_progress_detail_orange.xml`。状态栏透明，浅色背景使用深色系统栏图标，导航栏与页面底色一致。

## 页面顺序

首次打开（当前）：启动页／首次开屏插屏 → 设备扫描 → 设备详情。

首次悬浮窗引导入口已暂时注释，`OverlayPermissionGuideActivity` 页面保留。恢复时需同时取消 `SplashActivity.initViews()` 中入口的注释，并将 `OnboardingStep.NOT_STARTED.restored()` 的目标改回 `OVERLAY`；当前该目标为 `DEVICE_SCAN`，防止 Splash 结束后再次进入悬浮窗引导。

- 设备详情点“清理”：已授权存储时进入垃圾扫描；未授权时显示存储权限说明弹窗。
- 存储授权成功：垃圾扫描 → 扫描完成插屏 → 可展开与勾选的结果列表 → 清理动画 → 清理完成插屏 → 完成引导页。
- 关闭存储弹窗或拒绝授权：电池扫描 → 电池详情（插屏）→ 点击 Check Now → 电池检查动画 → 检查完成插屏 → 完成引导页。
- 设备详情点 Skip：尝试展示跳过插屏后进入首页。当前默认显示 Skip，不依赖参考项目的远程配置服务。
- 完成页有通知访问权限弹窗，授权后进入本应用的通知清理页；返回时如未允许悬浮窗权限，会先出现参考样式的权限提示，可选 Not Now 回首页。
- 完成页推荐入口接入本应用的 RAM 状态、通知清理、电池信息页面。

`SplashActivity` 负责开屏和首次／后续启动路由。每个功能页面拥有独立 Activity 和 ViewBinding；`OnboardingNavigator.kt` 仅供启动页选择恢复目标，`OnboardingState.kt` 持久化进度。旧版已完成 `device_scan_complete` 的用户继续跳过新手引导。进程重启后，垃圾结果与清理中的旧文件路径不会直接复用，而是重新扫描。

## Activity 与职责

所有新手页面位于 `ui/onboarding/`，每个 Activity 对应一个独立布局。页面内直接声明下一页跳转，不使用统一 Activity 切换布局。

| Activity | 职责 |
| --- | --- |
| `OverlayPermissionGuideActivity` | 首次悬浮窗与通知权限引导 |
| `DeviceStatusScanActivity` | 设备扫描动画与扫描完成插屏 |
| `DeviceStatusDetailActivity` | 设备详情、存储权限分支、跳过 |
| `BatteryInfoScanActivity` | 电池扫描动画与广告预加载 |
| `BatteryInfoActivity` | 新手电池详情、实时电量监听与检查入口 |
| `BatteryCheckScanActivity` | 电池检查动画与完成插屏 |
| `FirstJunkStartScanningActivity` | 垃圾扫描、进度与结果跳转 |
| `FirstJunkResultActivity` | 类别展开、选择与清理入口 |
| `FirstJunkCleaningActivity` | 执行清理、清理动画与完成插屏 |
| `FirstJunkCompleteActivity` | 清理／电池完成结果、权限提示和推荐入口 |

`BaseOnboardingActivity` 只复用系统栏、跳转防重复、进度动画、广告展示与弹窗窗口配置，不包含页面切换或分支业务。`OnboardingSettingsPermission` 复用系统设置授权回调与轮询。扫描和清理各有专属 ViewModel，避免 Activity 重建时重复执行任务；`FirstRunJunkSession` 仅在进程内传递扫描结果，完成后释放。完成页通过 Intent 接收实际清理大小或电池检查来源。

新手的 `ui/onboarding/BatteryInfoActivity` 与现有 `ui/main/BatteryInfoActivity` 分开维护，后者继续作为首页电池功能。

## 广告位

业务只引用 `AppAdManager` 中的场景常量，实际 AdMob ID 使用现有 `library_ad_sdk/src/main/res/raw/spaces.json`，未复制参考项目的广告 ID。

| 场景 | 常量 | 项目广告位 |
| --- | --- | --- |
| 首次开屏 | `FIRST_START` | `3007` |
| 后续冷启动 | `COLD_START` | `3001` |
| 热启动 | `HOT_START` | `3002` |
| 设备扫描完成 | `ONBOARDING_DEVICE_SCAN` | `3003` |
| 垃圾扫描完成／电池详情 | `ONBOARDING_ANALYSIS` | `3004` |
| 清理完成／电池检查完成 | `ONBOARDING_FINISH` | `3005` |
| 设备详情跳过 | `ONBOARDING_SKIP` | `3006` |
| 完成页原生广告 | `ONBOARDING_NATIVE` | `5001` |

插屏在扫描过程中预加载，加载失败时继续下一页；展示后等待关闭再继续。页面跳转和展示等待 Activity 恢复前台；系统权限页返回使用热启动跳过标记，避免插入额外的热启动流程。

## 后续替换资源

- 布局：`res/layout/onboarding_*.xml`。
- 图片、形状、进度条：`res/drawable*/onboarding_*` 和 `res/mipmap*/onboarding_*`。
- 当前动画：上表的 `res/raw/onboarding_orange_*.json`；原型源文件在 `designs/onboarding-orange/animations/`。旧参考动画资源仍保留，但新手页面已切换到橙色动画。
- 新增文案：`res/values/strings.xml` 中的 `string_onboarding_*`；相同文案复用已有字符串。
- 新增颜色：`res/values/colors.xml` 中按 `color_AARRGGBB` 命名的资源。
- 启动页：保留原有 `activity_splash.xml`；进度动画在 `ui/main/SplashActivity.kt` 中维护。
- 按钮轻微扫光：`OnboardingButtonAnimation.kt`。

背景继续延伸到透明状态栏后方，前景内容通过共用 `SystemBarInsets` 避开系统栏。当前新手页面均为浅色背景，使用深色状态栏图标。

## 扫描结果与验证

`FirstRunJunkScanner.kt` 保留参考项目的五类结果及下载／APK 默认不勾选的行为，增加遍历边界与重复路径保护，清理结果累计实际删除的文件大小。没有垃圾时显示真实的 0 B 并允许继续；未移植参考项目随机生成 500–1500 MB 缓存的兜底逻辑。

动态分类与子项列表使用 RecyclerView 和 ViewBinding。新手路由和选择计数测试在 `OnboardingFlowTest.kt`。

验证命令（JDK 17 或更新）：

```sh
sh gradlew :app:assembleDebug :app:testDebugUnitTest
```

实机验证使用独立包名 `com.nice.aceclean.onboardingverify`，保留现有正式包数据；清理测试只操作独立测试包自有缓存，未授予共享存储权限。
