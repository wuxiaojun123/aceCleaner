# 新手流程 · Orange

设计依据：首页 fragment_home.xml 的布局、白色 22dp 圆角卡片与柔和背景；设备信息页的 #FF6B2C 主色。

范围：启动、设备扫描与详情、存储授权、垃圾扫描/结果/清理/完成、电池扫描/详情/检查、通知与悬浮窗引导，以及固定首页作为终点。首次悬浮窗引导保留为可单独预览的备用页面，默认流程不进入。

本目录保存交互原型与动画源文件，对应 Android 页面现已接入，维护说明见项目根目录 ONBOARDING.md。HTML 原型内的设备及扫描数值仍为演示数据；原生页面使用真实设备信息、扫描与清理结果。HTML 权限与广告是模拟，Android 则保留现有真实接口。

## 打开与体验

在项目根目录运行：

```sh
python3 -m http.server 4311 --bind 127.0.0.1 --directory designs
```

- 页面设计预览：http://localhost:4311/onboarding-orange/index.html?auto=0#deviceDetail
- 从头体验：http://localhost:4311/onboarding-orange/index.html
- 动画展示与下载：http://localhost:4311/onboarding-orange/animations.html

左侧可直接选择每个页面；“从头体验新手流程”会启动连续演示。右侧可暂停扫描、暂停 Lottie、关闭模拟插屏，以及关闭完成页自动弹窗。手机窄屏通过右上方“预览导航”切换页面。设备信息页在小屏可滚动，主要按钮固定在底部。

存储权限弹窗点 Allow 后，在模拟系统设置中打开开关并点 Done，进入垃圾扫描；关闭权限弹窗或不打开系统开关，则进入电池分支。结果页可以展开分类、勾选子项；下载及 APK 默认不选。没有选中项时按钮变为 Continue，完成页显示 0 B。

完成页保留通知权限、离开时的悬浮窗提示，以及三个推荐入口。推荐入口在本原型中结束于固定首页，并在设计说明区显示目标功能名称。首页按已有 XML 和资源参照复现，不在本次重设计范围内。

## 配套动画

动画都在 `animations/`，透明背景、320×320、30 fps、90 帧，全部为形状图层，不依赖图片、字体或表达式。

| 文件 | 用途 | 播放 |
| --- | --- | --- |
| `device-scan.json` | 设备扫描线、设备轮廓 | 3 秒循环 |
| `junk-scan.json` | 文件列表扫描 | 3 秒循环 |
| `junk-clean.json` | 扫帚与碎屑清理 | 3 秒循环 |
| `battery-scan.json` | 电池信息读取 | 3 秒循环 |
| `battery-check.json` | 电池检查完成 | 播放一次后停留 |
| `complete.json` | 清理／检查成功反馈 | 播放一次后停留 |

可在动画展示页逐个下载 JSON。`build-animations.py` 是可重复生成脚本；传入官方播放器目录时，会同时写入其 `public/projects/ace-onboarding/scene-N/lottie.json`。原型预览使用 Skia Skottie；动画现已接入 Android Activity，并使用项目的 Lottie 6.5.2 在 Android 11 真机完成六个场景、18 个关键帧的解析与绘制检查，兼容警告为 0。

## 文件与维护

- `index.html`：原型入口。
- `app.jsx`：流程、权限模拟、选择与金额状态。
- `components.jsx`：独立页面、公共组件与动画播放。
- `prototype.css`：设计颜色、尺寸和响应式样式。
- `animations.html`：六个 Lottie 的播放、暂停、重播与下载。
- `assets/`：从当前项目定向复制或转换的图片与图标。
- `vendor/`：固定版本 React 18.3.1、React DOM 18.3.1、Babel 7.29.0，以及 CanvasKit 0.41.1 完整运行时。资源均本地加载，原型运行不依赖 CDN。
- `_d_meta.json`：baoyu-design 的交付版本索引。

广告仅模拟原项目 3007、3003、3004、3005、3006 插屏节点与 5001 原生广告占位，没有接入 SDK。备用悬浮窗引导只可单独预览，默认路径继续从 Splash 开始。

设计主色 `#FF6B2C`；浅橙 `#FFF2EA`；暖白 `#FAF9F7`；深灰 `#171D27`。白色圆角卡片继承首页风格，新增动画统一为克制的橙色线条与形状。

## 验证

已完成权限主线与电池分支、分类与子项勾选、金额同步、空选择继续、插屏关闭、完成页权限、页面直达链接、从头体验，以及 390px / 375px 窄屏检查。六个动画均在官方 Skottie 播放器检查第 0 / 45 / 89 帧。运行错误为 0，详情见 `validation.json`；静态预览在 `previews/`。
