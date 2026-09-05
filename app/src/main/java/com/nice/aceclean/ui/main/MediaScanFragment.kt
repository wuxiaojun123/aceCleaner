package com.nice.aceclean.ui.main

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.core.animation.doOnEnd
import com.airbnb.lottie.LottieAnimationView
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment

class MediaScanFragment : BaseFragment(R.layout.fragment_media_scan) {

    private var animator: ValueAnimator? = null

    override fun initViews(root: View) {
        val type = StorageCleanerType.valueOf(requireArguments().getString(ARG_TYPE).orEmpty())
        root.findViewById<LottieAnimationView>(R.id.media_scan_animation).setAnimation(when (type) {
            StorageCleanerType.SCREENSHOT -> R.raw.picture_clean_scan_anim
            StorageCleanerType.BIG_FILE -> R.raw.big_file_scan_anim
            StorageCleanerType.VIDEO -> R.raw.video_clean_scan_anim
        })
        root.findViewById<TextView>(R.id.media_scan_label).setText(when (type) {
            StorageCleanerType.SCREENSHOT -> R.string.scanning_picture
            StorageCleanerType.BIG_FILE -> R.string.scanning_large_files
            StorageCleanerType.VIDEO -> R.string.scanning_video
        })
        val percentage = root.findViewById<TextView>(R.id.media_scan_percentage)
        animator = ValueAnimator.ofInt(0, 100).apply {
            duration = 2_200L
            interpolator = LinearInterpolator()
            addUpdateListener { percentage.text = getString(R.string.scan_percentage, it.animatedValue as Int) }
            doOnEnd { (activity as? StorageCleanerActivity)?.showResult() }
            start()
        }
    }

    override fun onDestroyView() {
        animator?.cancel()
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TYPE = "storage_cleaner_type"

        fun newInstance(type: StorageCleanerType) = MediaScanFragment().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, type.name) }
        }
    }
}
