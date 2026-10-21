package com.nice.aceclean.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.databinding.ItemInterceptedNotificationBinding
import java.util.Date
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class InterceptedNotificationAdapter :
    ListAdapter<InterceptedNotification, InterceptedNotificationAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemInterceptedNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false),
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

    inner class ViewHolder(binding: ItemInterceptedNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        private val icon = binding.interceptedAppIcon
        private val fallback = binding.interceptedAppIconFallback
        private val title = binding.interceptedNotificationTitle
        private val content = binding.interceptedNotificationContent
        private val time = binding.interceptedNotificationTime
        private val timeFormatter = android.text.format.DateFormat.getTimeFormat(itemView.context)

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
            fallback.text = item.appName.firstOrNull()?.uppercaseChar()?.toString()
                ?: itemView.context.getString(R.string.string_app_icon_fallback)
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
