# 项目规则

- 不要使用 `autoSizeMaxTextSize`、`autoSizeMinTextSize` 和 `autoSizeStepGranularity` 属性，包括 `app:`、`android:` 命名空间以及 style 中的配置。文本字号使用明确的 `textSize`；移除已有的这三个属性时，同时移除对应的 `autoSizeTextType="uniform"`，避免继续按默认范围自动缩放。
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

## 可选效果：上滑渐显固定标题栏

- 此效果按页面设计和用户需求选用，不是所有页面的默认要求。适用于顶部有大标题、插图或概览区域，且上滑后仍需保留标题、返回键或功能入口的页面。已有常驻工具栏或内容无需滚动的页面，不必额外添加。
- 参考实现：`HomeFragment`、`NotificationCleanerActivity` 及其布局。使用 `NestedScrollView` 滚动内容，在其上方叠放固定标题栏；需要协调其他滚动行为时可使用 `CoordinatorLayout`。渐显由滚动监听实现，仅添加 `CoordinatorLayout` 不会自动产生此效果。
- 固定标题栏初始隐藏且 `alpha = 0`。根据滚动距离计算 `alpha = (scrollY.toFloat() / showDistance).coerceIn(0f, 1f)`，对整个标题栏设置透明度，`alpha > 0` 时显示，滑回顶部时隐藏。渐显距离可根据原始标题区域的高度和顶部间距确定。
- 状态栏始终透明，不通过滚动监听直接修改系统状态栏颜色。让固定标题栏自身的背景延伸到状态栏后方，随整个标题栏一起渐显，确保状态栏区域和标题栏背景连续，避免一块保持渐变、一块变为纯色。
- 复用 `SystemBarInsets` 的 `system_bar_top_overlay` 支持：根容器的第一个子视图为铺满窗口、标记 `android:tag="system_bar_background"` 的背景视图（可为带 drawable 背景的 `View` 或 `ImageView`）；固定标题栏也作为该根容器的直接子视图，设置 `android:tag="system_bar_top_overlay"`，并绘制在滚动内容上方。不要把固定栏放入已经整体避开状态栏的容器，否则背景无法覆盖状态栏区域。
- 固定栏在 XML 中设置明确的内容高度（如 `56dp`）。共用工具会在原始高度上增加状态栏顶部 inset，并在原始顶部 padding 上增加相同 inset；背景延伸到状态栏后方，标题和按钮仍避开状态栏。不要再给固定栏添加状态栏高度的顶部 margin，也不要重复手动叠加 inset。
- 保留原始标题区和固定栏的入口行为一致；固定栏完全显示后，将原始标题区设为 `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`，隐藏时恢复，避免重复朗读。布局完成后根据当前滚动位置刷新固定栏，兼顾页面恢复时的显示状态。
- 验证顶部初始状态、上滑渐显、滑回顶部隐藏、返回键或功能入口，以及状态栏与标题栏背景是否连续；状态栏图标明暗应适合渐显前后的背景。
