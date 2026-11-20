package com.nice.library_float.window.linker

import android.view.View
import android.widget.LinearLayout
import androidx.core.view.doOnLayout
import com.nice.library_float.window.manager.FloatWindowManager
import com.nice.library_float.window.util.FloatUtil

internal object FloatWindowLinker {
    fun link(mainTag: String, tipTag: String, offsetY: Int, space: Int, followDrag: Boolean) {
        val manager = FloatWindowManager.getInstance()
        val main = manager.getFloatWindow(mainTag) ?: return
        val tip = manager.getFloatWindow(tipTag) ?: return
        val refresh = {
            val ballView = main.contentView
            val tipView = tip.contentView
            val width = FloatUtil.bounds(ballView.context).width()
            val right = main.currentX + ballView.width / 2 >= width / 2
            (tipView as? LinearLayout)?.layoutDirection = if (right) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            val x = if (right) main.currentX - tipView.width - space else main.currentX + ballView.width + space
            tip.updatePosition(x.coerceIn(0, (width - tipView.width).coerceAtLeast(0)), main.currentY + offsetY + (ballView.height - tipView.height) / 2)
        }
        main.contentView.doOnLayout { tip.contentView.doOnLayout { refresh() } }
        main.positionChanged = {
            if (followDrag) refresh() else tip.setVisible(false)
        }
    }

    fun unlink(mainTag: String) {
        FloatWindowManager.getInstance().getFloatWindow(mainTag)?.positionChanged = null
    }
}
