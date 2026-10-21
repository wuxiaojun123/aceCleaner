# 项目规则

- 不要使用 `autoSizeMaxTextSize`、`autoSizeMinTextSize` 和 `autoSizeStepGranularity` 属性，包括 `app:`、`android:` 命名空间以及 style 中的配置。文本字号使用明确的 `textSize`；移除已有的这三个属性时，同时移除对应的 `autoSizeTextType="uniform"`，避免继续按默认范围自动缩放。
- 日志打印统一使用 `LogUtils`，不要直接使用 `android.util.Log`、`println` 或 `System.out` 打印日志。
- 对于展示数量不固定的应用、文件或扫描结果列表，使用带适配器和 ViewHolder 的 `RecyclerView`。不要在 `ScrollView` 内反复向 `LinearLayout` 添加列表项。
- 在 Android Activity、Fragment、Dialog 和 `RecyclerView` 的 ViewHolder 中，使用生成的 ViewBinding 类访问布局视图。不要为应用布局中的视图新增 `findViewById` 调用，也不要使用封装了 `findViewById` 的方法（例如 `BaseActivity.view()`）。修改仍然通过视图 ID 直接查找视图的页面时，应将该页面迁移到 ViewBinding。
- 向 `res/values/colors.xml` 添加颜色时，资源名称必须是 `color_` 加上大写的八位 ARGB 十六进制值，例如 `<color name="color_FF42B8FF">#FF42B8FF</color>`。添加前先检查是否已有相同颜色值的资源；如果有，直接复用，不要重复添加。
- 用户可见的字符串不要在 Kotlin、Java 或布局 XML 中硬编码；统一添加到 `res/values/strings.xml`，资源名称以 `string_` 开头，并通过 `R.string.string_xxx`、`getString(...)` 或 `@string/string_xxx` 引用。已有相同文案时，优先复用现有字符串资源。
- 字符串集中在各模块及各语言目录自己的 `strings.xml` 中，不要新增 `strings_xxx.xml`。纯预览/协议/符号可按需使用 `translatable="false"`，实际需要翻译的界面文案不要标记为不可翻译。
- 修改文案后运行 `python3 tools/check_string_resources.py` 和 `sh gradlew :app:checkLocalization`。正式包的 `preReleaseBuild` 自动执行资源检查，正常 APK/AAB 发布构建还会运行 fatal Android Lint；不要跳过检查或用 suppression 掩盖硬编码。检查范围与译文补齐要求见 `tools/README-localization.md`。

## 多语言翻译与资源同步

- 用户要求按 `strings.xml` 适配其他语言时，直接执行翻译并写回资源文件，不要只回答“可以”、给出方案或要求用户复制到翻译网站。默认由模型直接翻译；除非用户明确指定，不要寻找、调用或排查在线翻译接口，也不要为此安装翻译工具、索取 API Key 或引入外部服务。工具用于资源处理和结果验证，不应成为翻译工作的前置条件。
- 以对应模块当前的 `src/main/res/values/strings.xml` 为唯一源文案，先读取用户已有修改，再枚举实际存在的语言目录。语言数量和资源条数应实时统计，不要照搬上一次任务的数据。保留 Android 语言限定符，例如 `values-in`，不要擅自改名或新增语言目录。
- 先列出缺失键、默认文案已变化的键、遗留英文和格式异常，保留仍准确的已有译文，分批补齐并及时写回。相同原文可复用经确认适合当前语境的译文；英语与译文相同不必然是遗漏，语言自称、技术名词和符号需按语义判断。不得复制英文冒充翻译，也不得仅因语言相近就整份套用另一语言而不检查。
- 尊重默认文件的 `translatable="false"`，这些资源通常由默认文件提供；清理会覆盖默认应用名、单位或预览值的旧本地化副本前，确认没有刻意的地区差异。不要修改用户的源文案或不可翻译标记来减少工作量。核对过时键时，也不要误删其他代码仍引用的资源或限定符专用资源。
- 保留资源名称、格式声明、数字含义、占位符及富文本结构。`%1$s`、`%1$d`、精度和 `%%` 必须保持类型与数量一致，可按语序调整位置；保留显式 `\n` 的数量，在目标语言的自然停顿处换行。含普通百分号且不用于格式化的文案，应保留 `formatted="false"`。
- 正确处理 XML 与 Android 两层转义：XML 特殊字符按 XML 规则编码，Android 引号按需要使用单个反斜杠转义（如 `\'`、`\"`），不要重复转义。对通过 `HtmlCompat.fromHtml` 展示的字符串，必须保留可还原的 HTML 标签及颜色属性，不能把应解析的标签变成屏幕上的字面文本。比较富文本时，先还原 Android 引号转义，避免把等价编码误判为不一致。
- 写完后，独立核对每种语言的可翻译键集合、重复键、空译文、占位符、显式换行和富文本标签。先确认 XML 可解析，再运行 `python3 tools/check_string_resources.py --report build/reports/localization/strings.json` 和 `sh gradlew :app:checkLocalization :app:processDebugResources`。现有覆盖率报告只列出缺失项，脚本通过本身不代表所有语言已翻译完整，必须另外确认缺失数为零。
- 纯字符串翻译通常不需要执行 R8、打完整正式包或运行无关测试。Gradle 验证受阻时，明确说明已完成的校验和未验证部分，不要宣称全部通过。未经真机或母语审校，不要把资源编译通过描述成已完成这些验证。
- 进度只报告实际完成并写入的语言、剩余范围和校验结果；遇到工具问题应及时止损，继续独立可完成的翻译。不要反复发送“正在补齐”却主要在排障，也不要把工具选择和协调成本交给用户。完成后简要报告更新范围、缺失数和实际通过的检查。

## ActivityGuard 移包与 JVM 访问权限

- release 构建会先由 ActivityGuard 改类名和包名，再执行 R8。Activity 因白名单保留原包名，不能假设同一 Kotlin 文件或源代码包中的类在 release 中仍然同包。曾发生 `RamStatusActivity` 访问 `RunningAppsAdapter`（最终混淆名 `he.z`）时抛出 `IllegalAccessError`。
- 被 Activity、Fragment、Service 或其他可能保留包名的类直接访问的独立辅助类（例如适配器、ViewHolder、模型），不要声明为文件级 `private class/object/interface`；优先使用 Kotlin `internal`（编译为 JVM `public`），或确有必要时使用 `public`。Java 中相应的类、构造器和跨包访问成员也必须具有合法的访问权限，不能依赖默认包可见性。
- 不要为避免此问题把所有 `private` 一律改成公开。私有成员、文件内私有函数以及与外部类一起迁移且只在同包使用的私有嵌套类可以保留；检查它们是否通过合法的 JVM 访问器调用，以及混淆后访问方与目标类是否仍同包。Kotlin 源码可见性不能代替 JVM 字节码可见性检查。
- 新增或修改辅助类、继承关系、ActivityGuard 白名单、移包规则、R8 配置时，必须检查受影响的类及构造器/成员访问。不要仅增加 R8 `-keep` 规则来掩盖 ActivityGuard 已经造成的非法跨包访问；若通过白名单修复，需要同时保证相关类仍在兼容的包中。
- 验证时先生成本次代码对应的 release 类和 ActivityGuard 映射（`:app:activityGuardReleaseClassTask`），再执行 `python3 tools/check_obfuscated_class_access.py`。脚本会检查 app、广告库、推送库编译类中的类引用和移包后的非公开类访问，输入缺失时必须报错；不要使用旧映射或旧编译产物得出通过结论。脚本不覆盖成员访问、反射或 R8 最终优化结果，仍需结合代码检查。
- 发布验证应检查最终 release APK/AAB 的 DEX 访问标记，并使用该产物回归受影响页面。遇到混淆名时先串联 `app/mapping.txt`（ActivityGuard）和 `app/build/outputs/mapping/release/mapping.txt`（R8）还原目标。debug 编译通过不能替代混淆产物验证；构建受阻、跳过检查或没有设备时必须明确说明验证范围，不能宣称完整 release 回归通过。

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
