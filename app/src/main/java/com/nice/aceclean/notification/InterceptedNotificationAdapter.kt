package com.nice.aceclean.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class InterceptedNotificationAdapter :
    ListAdapter<InterceptedNotification, InterceptedNotificationAdapter.ViewHolder>(DiffCallback) {

    private val timeFormatter: DateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_intercepted_notification, parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    suspend fun clearDisplayedItems() {
        if (currentList.isEmpty()) return
        suspendCancellableCoroutine { continuation ->
            submitList(emptyList()) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<ImageView>(R.id.intercepted_app_icon)
        private val fallback = view.findViewById<TextView>(R.id.intercepted_app_icon_fallback)
        private val title = view.findViewById<TextView>(R.id.intercepted_notification_title)
        private val content = view.findViewById<TextView>(R.id.intercepted_notification_content)
        private val time = view.findViewById<TextView>(R.id.intercepted_notification_time)

        fun bind(item: InterceptedNotification) {
            itemView.translationX = 0f
            itemView.alpha = 1f
            title.text = item.title.ifBlank { item.appName }
            content.text = item.content
            content.visibility = if (item.content.isBlank()) View.GONE else View.VISIBLE
            time.text = timeFormatter.format(Date(item.postedAt))
            val drawable = AppIconLoader.load(itemView.context, item.packageName)
            icon.visibility = if (drawable != null) View.VISIBLE else View.GONE
            fallback.visibility = if (drawable == null) View.VISIBLE else View.GONE
            icon.setImageDrawable(drawable)
            fallback.text = item.appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            icon.contentDescription = item.appName
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<InterceptedNotification>() {
        override fun areItemsTheSame(oldItem: InterceptedNotification, newItem: InterceptedNotification) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: InterceptedNotification, newItem: InterceptedNotification) =
            oldItem == newItem
    }
}
