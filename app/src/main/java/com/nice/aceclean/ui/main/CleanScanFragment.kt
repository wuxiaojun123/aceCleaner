package com.nice.aceclean.ui.main

import android.view.View
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.nice.aceclean.R
import com.nice.aceclean.cleanup.CleanupViewModel
import com.nice.aceclean.ui.base.BaseFragment
import kotlinx.coroutines.launch

class CleanScanFragment : BaseFragment(R.layout.fragment_clean_scan) {

    private val viewModel: CleanupViewModel by activityViewModels()

    override fun initViews(root: View) {
        val progressText = root.findViewById<TextView>(R.id.scan_percentage)
        progressText.text = getString(R.string.scanned_files_progress, 0)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scanProgress.collect { progress ->
                    progressText.text = getString(R.string.scanned_files_progress, progress.scannedFiles)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scan()
            (activity as? CleanUpActivity)?.showCleanResult()
        }
    }
}
