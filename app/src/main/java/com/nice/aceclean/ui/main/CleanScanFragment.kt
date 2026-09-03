package com.nice.aceclean.ui.main

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.core.animation.doOnEnd
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment

class CleanScanFragment : BaseFragment(R.layout.fragment_clean_scan) {

    private var scanAnimator: ValueAnimator? = null

    override fun initViews(root: View) {
        val progressText = root.findViewById<TextView>(R.id.scan_percentage)
        scanAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = SCAN_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { progressText.text = getString(R.string.scan_percentage, it.animatedValue as Int) }
            doOnEnd { (activity as? MainActivity)?.showCleanResult() }
            start()
        }
    }

    override fun onDestroyView() {
        scanAnimator?.cancel()
        super.onDestroyView()
    }

    private companion object {
        const val SCAN_DURATION_MS = 3_200L
    }
}
