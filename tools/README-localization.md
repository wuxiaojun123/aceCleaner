# Android 字符串与发布检查

默认文案集中到各模块 `src/main/res/values/strings.xml`，对应译文保留在
`values-语言/strings.xml`。新增名称使用 `string_` 前缀；已有相同文案优先复用。
预览资源也放在 `strings.xml`，纯 IDE 示例可标记 `translatable="false"`。
不要把实际需要翻译的英文句子标记为不可翻译。

## 检查命令

```sh
python3 tools/check_string_resources.py
python3 -m unittest discover -s tools -p 'test_check_string_resources.py'
sh gradlew :app:checkLocalization
```

脚本只依赖 Python 3 标准库。它检查 app、library_push、library_ad_sdk 的源文件，
拦截分散在其他 XML 中的字符串资源、同配置重复资源、布局/菜单/Manifest/style
中的硬编码文案，以及 Kotlin/Java 中直接写给文本属性、对话框、Toast、Snackbar、
通知和 RemoteViews 的字符串字面量。`tools:` 预览、测试夹具、日志、协议常量不作为界面文案。

Android Lint 补充语义检查：`HardcodedText`、`SetTextI18n`、`StringFormatInvalid`、
`StringFormatMatches`、`StringFormatCount` 均设为 fatal，包含本项目依赖库。
`:app:checkLocalization` 运行脚本及 `lintVitalRelease`，不会执行 R8 混淆或打包。

## 每次正式包自动检查

各模块 `preReleaseBuild` 依赖脚本检查；app 的正常 `assembleRelease` 和 `bundleRelease`
还会执行 Android Gradle Plugin 的 release fatal Lint 检查。发现问题时构建失败并给出位置。
不要在发布命令中使用 `-x checkStringResources` 或 `-x lintVitalRelease` 绕过检查。

动态文案应使用带位置的占位符，例如 `getString(R.string.string_title, count)`，
不要把翻译后的文案与英文词语拼接。用户输入、文件名、应用名、广告或新闻服务器返回的内容
不是静态资源；检查无法证明任意变量的数据流中都不存在硬编码，仍需代码审查。

本检查保证文案进入资源和基本格式正确，不保证译文完整性或翻译质量。新增可翻译资源后，
需要补齐支持语言的译文；缺失时 Android 会回退到默认语言，不应复制英文冒充已翻译。
Gradle 检查会生成 `build/reports/localization/strings.json`，列出各模块已有语言目录缺少的资源键，
供翻译补齐时使用。此覆盖率报告只统计 `src/main` 的资源键，不分析限定符优先级或译文质量。
