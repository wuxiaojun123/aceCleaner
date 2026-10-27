# 清理扫描动画

这组动画沿用项目现有扫描 Lottie 的视觉与运动：带高光的渐变球体、细刻度旋转环和外侧白色细环。扫描时中心图标淡入淡出；清理时扫帚轻微往复摆动，闪光依次出现。相似图片、截图和清理沿用蓝色球体，大文件沿用原来的暖黄色球体。

| 功能 | 可预览 JSON | 打包文件 | 球体图片 |
| --- | --- | --- | --- |
| 相似图片 | `scan-photos.json` | `scan-photos.lottie` | `orb-blue.webp` |
| 截图清理 | `scan-screenshots.json` | `scan-screenshots.lottie` | `orb-blue.webp` |
| 大文件 | `scan-big-files.json` | `scan-big-files.lottie` | `orb-amber.webp` |
| 清理中 | `scan-cleaning.json` | `scan-cleaning.lottie` | `orb-blue.webp` |

规格：375 × 375、30 fps、90 帧（3 秒），透明背景。JSON 需要同目录下的 WebP；`.lottie` 文件已将图片打包在内。运行 `python3 build-scan-lotties.py` 可从项目的 `picture_clean_scan_anim.lottie` 和 `big_file_scan_anim.lottie` 重新生成这些文件。

HTML 原型在进入相应结果页前播放扫描动画；点击 System Cleaning 页的 Clean Now 后播放清理动画，完成后显示原有结果弹窗。左上角返回按钮可取消。正式 Android 页面尚未接入这些新文件；接入时需在设备上确认所用 Lottie 播放器对 `.lottie` 内图片的支持及实际显示尺寸。
