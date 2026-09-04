package com.nice.aceclean.ui.main

import android.app.Notification
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.nice.aceclean.R
import com.nice.aceclean.service.NotificationCollectorService
import com.nice.aceclean.ui.base.BaseFragment

class NotificationCleanerFragment : BaseFragment(R.layout.fragment_notification_cleaner) {

    private lateinit var rootView: View

    override fun initViews(root: View) {
        rootView = root
        root.findViewById<View>(R.id.notification_back).setOnClickListener { requireActivity().finish() }
        root.findViewById<View>(R.id.notification_clean_all).setOnClickListener {
            if (NotificationCollectorService.clearAll()) {
                rootView.findViewById<LinearLayout>(R.id.notification_list).removeAllViews()
                rootView.findViewById<TextView>(R.id.notification_empty).visibility = View.VISIBLE
            } else {
                Toast.makeText(requireContext(), R.string.notification_access_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        refreshNotifications()
    }

    override fun onResume() {
        super.onResume()
        if (::rootView.isInitialized) refreshNotifications()
    }

    private fun refreshNotifications() {
        val notifications = NotificationCollectorService.notifications()
        val container = rootView.findViewById<LinearLayout>(R.id.notification_list)
        val empty = rootView.findViewById<TextView>(R.id.notification_empty)
        container.removeAllViews()
        empty.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE

        notifications.forEach { status ->
            val item = LayoutInflater.from(requireContext()).inflate(R.layout.item_notification, container, false)
            val extras = status.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: runCatching { requireContext().packageManager.getApplicationLabel(requireContext().packageManager.getApplicationInfo(status.packageName, 0)).toString() }.getOrDefault(status.packageName)
            val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            item.findViewById<TextView>(R.id.notification_title).text = title
            item.findViewById<TextView>(R.id.notification_text).text = body
            runCatching { requireContext().packageManager.getApplicationIcon(status.packageName) }
                .onSuccess { item.findViewById<ImageView>(R.id.notification_app_icon).setImageDrawable(it) }
            item.findViewById<View>(R.id.notification_delete).setOnClickListener {
                NotificationCollectorService.clear(status.key)
                container.removeView(item)
                empty.visibility = if (container.childCount == 0) View.VISIBLE else View.GONE
            }
            container.addView(item)
        }
    }
}
