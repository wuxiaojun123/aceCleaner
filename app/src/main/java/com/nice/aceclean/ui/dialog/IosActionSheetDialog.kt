package com.nice.aceclean.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import com.nice.aceclean.R

/** Reusable iOS-style action sheet for non-destructive confirmations and choices. */
object IosActionSheetDialog {

    data class Action(
        val text: CharSequence,
        @ColorInt val color: Int = IOS_BLUE,
        val onClick: () -> Unit,
    )

    fun show(
        activity: Activity,
        title: CharSequence,
        message: CharSequence? = null,
        actions: List<Action>,
        cancelText: CharSequence = activity.getString(android.R.string.cancel),
        onCancel: () -> Unit = {},
    ): Dialog {
        require(actions.isNotEmpty()) { "An action sheet requires at least one action." }
        val content = activity.layoutInflater.inflate(R.layout.dialog_ios_action_sheet, null)
        val dialog = Dialog(activity).apply {
            setContentView(content)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setGravity(Gravity.BOTTOM)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = DIM_AMOUNT }
            }
        }
        content.findViewById<TextView>(R.id.ios_action_sheet_title).text = title
        content.findViewById<TextView>(R.id.ios_action_sheet_message).apply {
            visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
            text = message ?: ""
        }
        val actionContainer = content.findViewById<LinearLayout>(R.id.ios_action_sheet_actions)
        actions.forEachIndexed { index, action ->
            if (index > 0) addDivider(actionContainer)
            actionContainer.addView(createActionView(activity, action) {
                dialog.dismiss()
                action.onClick()
            })
        }
        content.findViewById<TextView>(R.id.ios_action_sheet_cancel).apply {
            text = cancelText
            setOnClickListener {
                dialog.dismiss()
                onCancel()
            }
        }
        dialog.setOnCancelListener { onCancel() }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    private fun createActionView(activity: Activity, action: Action, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ACTION_HEIGHT_DP.dp(activity),
            )
            background = activity.getDrawable(android.R.drawable.list_selector_background)
            gravity = Gravity.CENTER
            text = action.text
            setTextColor(action.color)
            textSize = 17f
            setOnClickListener { onClick() }
        }

    private fun addDivider(container: LinearLayout) {
        container.addView(View(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DIVIDER_HEIGHT_DP.dp(container.context),
            )
            setBackgroundColor(DIVIDER_COLOR)
        })
    }

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()

    private const val ACTION_HEIGHT_DP = 56
    private const val DIVIDER_HEIGHT_DP = 1
    private const val DIM_AMOUNT = 0.36f
    private const val IOS_BLUE = -16_745_729 // #007AFF
    private const val DIVIDER_COLOR = 0x1F000000
}
