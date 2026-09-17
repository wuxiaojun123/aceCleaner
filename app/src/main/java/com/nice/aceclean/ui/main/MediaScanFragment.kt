package com.nice.aceclean.ui.main

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.util.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaScanFragment : BaseFragment(R.layout.fragment_media_scan) {

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
        percentage.text = getString(R.string.scan_percentage, 0)
        viewLifecycleOwner.lifecycleScope.launch {
            percentage.text = getString(R.string.scan_percentage, 10)
            withContext(Dispatchers.IO) {
                when (type) {
                    StorageCleanerType.SCREENSHOT -> MediaStoreRepository.pictures(requireContext())
                    StorageCleanerType.BIG_FILE -> MediaStoreRepository.largeFiles(requireContext())
                    StorageCleanerType.VIDEO -> MediaStoreRepository.videos(requireContext())
                }
            }
            percentage.text = getString(R.string.scan_percentage, 100)
            (activity as? StorageCleanerActivity)?.showResult()
        }
    }

    companion object {
        private const val ARG_TYPE = "storage_cleaner_type"

        fun newInstance(type: StorageCleanerType) = MediaScanFragment().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, type.name) }
        }
    }
}
