package com.nice.aceclean.ui.main

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.DialogQuickCleanBottomSheetBinding
import com.nice.aceclean.util.EventUtils

/** iOS-inspired bottom sheet shown before asking Android to add the Quick Settings tile. */
class QuickCleanBottomDialogFragment : DialogFragment() {

    private var resultReported = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogQuickCleanBottomSheetBinding.inflate(layoutInflater)
        return Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(binding.root)
            setCanceledOnTouchOutside(true)
            binding.quickTileAdd.setTrackedClickListener(EventUtils.CLICK_QUICK_TILE_ADD) {
                reportResult(addRequested = true)
                dismiss()
            }
            binding.quickTileNotNow.setTrackedClickListener(EventUtils.CLICK_QUICK_TILE_LATER) {
                reportResult(addRequested = false)
                dismiss()
            }
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.BOTTOM)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = DIM_AMOUNT }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        reportResult(addRequested = false)
        super.onDismiss(dialog)
    }

    private fun reportResult(addRequested: Boolean) {
        if (resultReported) return
        resultReported = true
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(EXTRA_ADD_REQUESTED to addRequested),
        )
    }

    companion object {
        const val RESULT_KEY = "quick_clean_bottom_sheet_result"
        const val EXTRA_ADD_REQUESTED = "add_requested"
        private const val TAG = "quick_clean_bottom_sheet"
        private const val DIM_AMOUNT = 0.36f

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                QuickCleanBottomDialogFragment().show(fragmentManager, TAG)
            }
        }
    }
}
