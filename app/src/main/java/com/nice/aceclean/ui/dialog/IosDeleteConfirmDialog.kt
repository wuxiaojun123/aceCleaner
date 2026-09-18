package com.nice.aceclean.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.ColorInt
import com.nice.aceclean.R

object IosDeleteConfirmDialog {

    fun show(
        activity: Activity,
        title: CharSequence,
        message: CharSequence? = null,
        cancelText: CharSequence = activity.getString(android.R.string.cancel),
        confirmText: CharSequence = activity.getString(R.string.delete),
        @ColorInt confirmColor: Int = Color.rgb(255, 59, 48),
        onConfirm: () -> Unit,
        onDismiss: () -> Unit = {},
    ): Dialog {
        val content = activity.layoutInflater.inflate(R.layout.dialog_ios_delete_confirm, null)
        val dialog = Dialog(activity).apply {
            setContentView(content)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = DIM_AMOUNT }
            }
        }
        content.findViewById<TextView>(R.id.ios_dialog_title).text = title
        content.findViewById<TextView>(R.id.ios_dialog_message).apply {
            visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
            text = message ?: ""
        }
        content.findViewById<TextView>(R.id.ios_dialog_cancel).apply {
            text = cancelText
            setOnClickListener { dialog.dismiss() }
        }
        content.findViewById<TextView>(R.id.ios_dialog_confirm).apply {
            text = confirmText
            setTextColor(confirmColor)
            setOnClickListener {
                dialog.dismiss()
                onConfirm()
            }
        }
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        val horizontalMargin = (HORIZONTAL_MARGIN_DP * activity.resources.displayMetrics.density).toInt()
        val maxWidth = (MAX_WIDTH_DP * activity.resources.displayMetrics.density).toInt()
        val width = (activity.resources.displayMetrics.widthPixels - horizontalMargin * 2).coerceAtMost(maxWidth)
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    private const val HORIZONTAL_MARGIN_DP = 36
    private const val MAX_WIDTH_DP = 320
    private const val DIM_AMOUNT = 0.42f
}
