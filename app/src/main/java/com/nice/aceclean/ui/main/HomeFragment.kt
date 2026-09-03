package com.nice.aceclean.ui.main

import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment

class HomeFragment : BaseFragment(R.layout.fragment_home) {

    override fun initViews(root: View) {
        root.findViewById<View>(R.id.clean_up_button).setOnClickListener {
            (activity as? MainActivity)?.openCleanUp()
        }

        listOf(
            R.id.settings_button,
            R.id.language_button,
            R.id.notification_card,
            R.id.network_traffic_card,
            R.id.notification_cleaner_card,
            R.id.app_manager_card,
            R.id.screenshot_cleaner_card,
            R.id.big_file_cleaner_card,
            R.id.duplicate_photo_cleaner_card,
            R.id.video_cleaner_card,
        ).forEach { id ->
            root.findViewById<View>(id).setOnClickListener { showComingSoon() }
        }
    }

    private fun showComingSoon() {
        Toast.makeText(requireContext(), getString(R.string.feature_coming_soon), Toast.LENGTH_SHORT).show()
    }
}
