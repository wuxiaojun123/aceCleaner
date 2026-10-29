package com.nice.aceclean.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * 首页存储占用圆环。
 *
 * 外观调整入口：
 * - 圆环粗细：修改 track 和 progress 的 strokeWidth，建议两处保持一致。
 * - 颜色：track 为底环，progress 为进度圆弧，dot 为所有装饰圆点，分别修改各自的 color。
 * - 圆点大小：上方两个圆点修改 drawCircle 中的 8f、10f；下方两个小点修改 spots 的第三项 1.6f。
 * - 整个圆环大小：由 View 的布局宽高和 onDraw 中的 radius 共同决定。
 */
class HomeStorageGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var usedPercent: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    // dp 转 px 的倍率：例如 17f * density 表示 17dp，数值越大，绘制尺寸越大。
    private val density = resources.displayMetrics.density
    // 完整的浅色底环。
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // 底环粗细，当前为 17dp；加粗或变细时通常同步修改 progress.strokeWidth。
        strokeWidth = 15f * density
        // 底环颜色：浅灰蓝色。直接替换 #RRGGBB 色值即可；需要透明度时使用 #AARRGGBB。
        color = Color.parseColor("#EDF3F5")
    }
    // 覆盖在底环上的已用空间进度圆弧。
    private val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // 进度圆弧粗细，当前为 17dp。
        strokeWidth = 15f * density
        // 圆弧两端使用圆头；改为 Paint.Cap.BUTT 可使用平头。
        strokeCap = Paint.Cap.ROUND
        // 进度颜色：蓝色；改变它不会自动改变下面的圆点颜色。
        color = Color.parseColor("#117CF9")
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // 所有装饰圆点共用此颜色：蓝色。
        color = Color.parseColor("#117CF9")
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        // 圆环中心线半径：20dp 是中心线到 View 短边边缘的预留距离。
        // 减小 20f 会扩大圆环，增大会缩小；预留距离至少应为描边宽度的一半，以免边缘被裁切。
        val radius = size / 2f - 20f * density
        oval.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawCircle(centerX, centerY, radius, track)
        canvas.drawArc(oval, -109f, 3.6f * usedPercent, false, progress)
        // 上方两个装饰圆点按 300 × 300 设计稿随 View 短边等比例缩放，这里的尺寸不是 dp。
        val referenceScale = size / 300f
        canvas.drawCircle(
            centerX + (37f - 150f) * referenceScale,
            centerY + (35f - 150f) * referenceScale,
            // 左上圆点半径：修改 8f 调整大小，直径为此值的两倍。
            6f * referenceScale,
            dot,
        )
        canvas.drawCircle(
            centerX + (279f - 150f) * referenceScale,
            centerY + (69f - 150f) * referenceScale,
            // 右上圆点半径：修改 10f 调整大小，直径为此值的两倍。
            8f * referenceScale,
            dot,
        )
        // 下方小圆点：每行依次为水平偏移比例、垂直偏移比例、半径（dp）。
        // 前两项相对圆环中心，以 radius 为单位；第三项 1.6f 表示半径 1.6dp（直径 3.2dp）。
        val spots = arrayOf(
            floatArrayOf(-0.80f, 0.83f, 1.6f),
            floatArrayOf(0.68f, 0.87f, 1.6f),
        )
        spots.forEach { spot ->
            canvas.drawCircle(
                centerX + radius * spot[0],
                centerY + radius * spot[1],
                spot[2] * density,
                dot,
            )
        }
    }
}
