# 项目规则

- 日志打印统一使用 `LogUtils`，不要直接使用 `android.util.Log`、`println` 或 `System.out` 打印日志。
- 对于展示数量不固定的应用、文件或扫描结果列表，使用带适配器和 ViewHolder 的 `RecyclerView`。不要在 `ScrollView` 内反复向 `LinearLayout` 添加列表项。
- 在 Android Activity、Fragment、Dialog 和 `RecyclerView` 的 ViewHolder 中，使用生成的 ViewBinding 类访问布局视图。不要为应用布局中的视图新增 `findViewById` 调用，也不要使用封装了 `findViewById` 的方法（例如 `BaseActivity.view()`）。修改仍然通过视图 ID 直接查找视图的页面时，应将该页面迁移到 ViewBinding。
- 向 `res/values/colors.xml` 添加颜色时，资源名称必须是 `color_` 加上大写的八位 ARGB 十六进制值，例如 `<color name="color_FF42B8FF">#FF42B8FF</color>`。添加前先检查是否已有相同颜色值的资源；如果有，直接复用，不要重复添加。
- 用户可见的字符串不要在 Kotlin、Java 或布局 XML 中硬编码；统一添加到 `res/values/strings.xml`，资源名称以 `string_` 开头，并通过 `R.string.string_xxx`、`getString(...)` 或 `@string/string_xxx` 引用。已有相同文案时，优先复用现有字符串资源。

## 沉浸式系统栏

- 应用页面的状态栏保持透明，让页面背景延伸到状态栏后方。根据背景亮度设置状态栏图标的明暗。不要用单独的纯色填充状态栏区域来模拟整屏图片或渐变背景。
- 将系统栏 inset 应用于前景内容，不要应用于包含整屏背景 `ImageView` 的根容器。移动整个根容器会使背景图从状态栏下方才开始绘制，形成可见的断层。如果纯色或渐变背景直接设置在根容器上，可以通过根容器的 padding 移动内容，同时让背景继续铺满整个容器。
- 使用整屏背景 `ImageView` 的布局，应将它放在根容器的第一个子视图，并设置 `android:tag="system_bar_background"`。共用的 `SystemBarInsets` 工具会让该视图保持全屏，并对其他直接子视图应用 inset。新增页面或调整背景结构时，也要相应检查和更新 inset 处理。
- Fragment 宿主容器应铺满窗口；在 Fragment 自身的布局中处理 inset，让背景也能延伸到状态栏。Activity 每次替换内容视图时，都要重新应用共用的 inset 处理。整屏背景的根容器不要设置 `fitsSystemWindows="true"`，以免背景被限制在系统栏下方。
