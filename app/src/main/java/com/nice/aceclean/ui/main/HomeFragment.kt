package com.nice.aceclean.ui.main

import android.view.View
import android.widget.Toast
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment

class HomeFragment : BaseFragment(R.layout.fragment_home) {

    override fun initViews(root: View) {
        root.findViewById<View>(R.id.clean_up_button).setOnClickListener {
            (activity as? MainActivity)?.openCleanUp()
        }
        root.findViewById<View>(R.id.network_traffic_card).setOnClickListener {
            (activity as? MainActivity)?.openNetworkTraffic()
        }
        root.findViewById<View>(R.id.notification_cleaner_card).setOnClickListener {
            (activity as? MainActivity)?.openNotificationCleaner()
        }
        root.findViewById<View>(R.id.app_manager_card).setOnClickListener {
            (activity as? MainActivity)?.openAppManager()
        }
        root.findViewById<View>(R.id.screenshot_cleaner_card).setOnClickListener {
            (activity as? MainActivity)?.openScreenshotCleaner()
        }
        root.findViewById<View>(R.id.big_file_cleaner_card).setOnClickListener {
            (activity as? MainActivity)?.openBigFileCleaner()
        }
        root.findViewById<View>(R.id.video_cleaner_card).setOnClickListener {
            (activity as? MainActivity)?.openVideoCleaner()
        }
        root.findViewById<View>(R.id.duplicate_photo_cleaner_card).setOnClickListener {
            (activity as? MainActivity)?.openDuplicatePhotoCleaner()
        }

        root.findViewById<View>(R.id.language_button).setOnClickListener {
            (activity as? MainActivity)?.openLanguage()
        }

        listOf(R.id.settings_button, R.id.notification_card).forEach { id ->
            root.findViewById<View>(id).setOnClickListener { showComingSoon() }
        }
    }

    private fun showComingSoon() {
        Toast.makeText(requireContext(), getString(R.string.feature_coming_soon), Toast.LENGTH_SHORT).show()
    }
}
