# 新手流程

参考项目：`cleanGo/cleanboxpro`。新手功能页面的布局、图片和 Lottie 动画先复用参考项目；应用名称与功能入口使用 AceClean 本身的配置。启动页沿用原有 `ActivitySplashBinding`、`activity_splash.xml`、Logo、横向进度条与加载文案，只接入新手路由和广告场景。

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
- 动画：`res/raw/onboarding_*.zip`、`onboarding_*.json`；ZIP 内包含对应图片，无需外部 assets 目录。
- 新增文案：`res/values/strings.xml` 中的 `string_onboarding_*`；相同文案复用已有字符串。
- 新增颜色：`res/values/colors.xml` 中按 `color_AARRGGBB` 命名的资源。
- 启动页：保留原有 `activity_splash.xml`；进度动画在 `ui/main/SplashActivity.kt` 中维护。
- 按钮呼吸与扫光：`OnboardingButtonAnimation.kt`。

背景继续延伸到透明状态栏后方，前景内容通过共用 `SystemBarInsets` 避开系统栏。深色扫描页面使用浅色状态栏图标，浅色详情页面使用深色图标。

## 扫描结果与验证

`FirstRunJunkScanner.kt` 保留参考项目的五类结果及下载／APK 默认不勾选的行为，增加遍历边界与重复路径保护，清理结果累计实际删除的文件大小。没有垃圾时显示真实的 0 B 并允许继续；未移植参考项目随机生成 500–1500 MB 缓存的兜底逻辑。

动态分类与子项列表使用 RecyclerView 和 ViewBinding。新手路由和选择计数测试在 `OnboardingFlowTest.kt`。

验证命令（JDK 17 或更新）：

```sh
sh gradlew :app:assembleDebug :app:testDebugUnitTest
```

实机验证使用独立包名 `com.nice.aceclean.onboardingverify`，保留现有正式包数据；清理测试只操作独立测试包自有缓存，未授予共享存储权限。
