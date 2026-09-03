package com.nice.aceclean.ui.main

import android.view.View
import android.widget.Toast
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment

class CleanResultFragment : BaseFragment(R.layout.fragment_clean_result) {

    override fun initViews(root: View) {
        root.findViewById<View>(R.id.clean_result_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        root.findViewById<View>(R.id.remove_button).setOnClickListener {
            Toast.makeText(requireContext(), R.string.clean_complete_message, Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }
}
