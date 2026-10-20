# TinyPNG 批量压缩脚本

`tinypng.py` 是独立脚本，仅依赖 Python 3.8+ 标准库。复制这个文件到其他项目即可使用，不依赖 Android、Gradle 或第三方 Python 包。所有相对路径均以**执行命令时所在目录**为准。

通过 [TinyPNG 官方 API](https://tinypng.com/developers/reference) 上传原图，再下载压缩结果。需要在 [开发者页面](https://tinypng.com/developers)申请 API Key；官网手动上传和 API 的账号/额度可能不同，以账户页面为准。实际执行会上传图片并消耗 API 压缩次数。

## 1. 先预览

在项目根目录执行：

```sh
python3 tools/tinypng.py app/src/main/res
```

未开启时只列出待处理图片，不联网、不创建备份、不修改原图。支持单文件、多个文件或多个目录：

```sh
python3 tools/tinypng.py assets/images public/images
python3 tools/tinypng.py path/to/icon.png
```

不传路径时扫描当前目录，自动排除 `build`、`dist`、`.git`、`.gradle`、`node_modules`、备份目录等。

## 2. 配置 API Key

macOS / Linux / zsh：

```sh
export TINYPNG_API_KEY='你的 API Key'
```

PowerShell：

```powershell
$env:TINYPNG_API_KEY = '你的 API Key'
```

Key 只从环境变量读取，不写入脚本、缓存或日志，不要提交到 Git。

## 3. 打开开关，开始压缩

推荐用命令行开关，先处理一张确认效果：

```sh
python3 tools/tinypng.py app/src/main/res --apply --limit 1
```

确认后批量处理：

```sh
python3 tools/tinypng.py app/src/main/res --apply
```

也可以把脚本顶部的 `ENABLED = False` 改为 `True`，之后运行就会实际处理。`--dry-run` 始终优先，可以强制预览。

## 处理规则

- 支持 PNG、JPG/JPEG、WebP；保持文件名、原格式和像素尺寸。
- 跳过 `.9.png`、动画 PNG/WebP、符号链接，以及当前缓存中内容未变化的已处理图片。
- 只在下载结果更小时替换；结果更大或相同则保留原图。
- 替换前备份原图，再用同目录临时文件进行原子替换；保留文件权限。
- 文件上传期间若被其他程序修改，会放弃替换，保留新改动。
- 下载失败会保留下载地址，下次运行优先重试下载，避免重复上传；地址过期时可删除缓存中该文件的条目后重试。
- 不会自动重试上传，避免不确定的网络失败导致重复消耗额度。
- Key 无效、配额不足或请求过频时停止批次；其他单文件失败继续处理，最终以非零退出码报告失败。
- 不做缩放或格式转换。压缩可能改变像素和移除元数据，应检查项目中的实际显示效果。

## 本地缓存和恢复

在执行目录下生成：

```text
.tinypng-cache.json       # 文件内容摘要及未完成的下载地址
.tinypng-backups/时间戳/  # 替换前的原始图片，保持相对路径
.tinypng.lock             # 防止同一执行目录内同时运行多个批次
```

复制项目内某张图片的备份到原路径即可恢复。执行目录之外的图片备份放在 `external/目录摘要/` 下。

进程被强制终止后若残留 `.tinypng.lock`，确认没有压缩程序运行后手动删除。不要同时从不同执行目录压缩相同图片。

在其他项目的 `.gitignore` 中加入：

```gitignore
.tinypng-cache.json
.tinypng-backups/
.tinypng.lock
```

缓存不要随意删除，否则下次运行会重新上传；备份自行按需要保留。

## 常用参数

```sh
# 排除特定图片或目录（通配符加引号，避免 shell 提前展开）
python3 tools/tinypng.py app/src/main/res --apply --exclude '*launcher*' --exclude '*/mipmap-*/*'

# 本次最多处理 20 张
python3 tools/tinypng.py app/src/main/res --apply --limit 20

# 强制再次压缩（会再次消耗 API 额度）
python3 tools/tinypng.py app/src/main/res --apply --force

# 查看帮助
python3 tools/tinypng.py --help
```

## 离线验证

```sh
python3 -m unittest discover -s tools -p 'test_tinypng.py' -v
```

测试使用临时文件和模拟 API，不上传项目图片，不消耗 API 额度。
