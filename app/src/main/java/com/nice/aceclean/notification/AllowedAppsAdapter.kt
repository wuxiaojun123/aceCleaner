package com.nice.aceclean.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.ItemAllowedAppBinding
import com.nice.aceclean.util.EventUtils

data class AllowedApp(val packageName: String, val appName: String)

class AllowedAppsAdapter(
    private val isAllowed: (String) -> Boolean,
    private val onAllowedChanged: (String, Boolean) -> Unit,
) : ListAdapter<AllowedApp, AllowedAppsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemAllowedAppBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(binding: ItemAllowedAppBinding) : RecyclerView.ViewHolder(binding.root) {
        private val icon = binding.allowedAppIcon
        private val fallback = binding.allowedAppIconFallback
        private val name = binding.allowedAppName
        private val packageName = binding.allowedAppPackage
        private val allowSwitch = binding.allowedAppSwitch

        fun bind(item: AllowedApp) {
            name.text = item.appName
            packageName.text = item.packageName
            val drawable = AppIconLoader.load(itemView.context, item.packageName)
            icon.visibility = if (drawable != null) View.VISIBLE else View.GONE
            fallback.visibility = if (drawable == null) View.VISIBLE else View.GONE
            icon.setImageDrawable(drawable)
            fallback.text = item.appName.firstOrNull()?.uppercaseChar()?.toString() ?: itemView.context.getString(R.string.string_app_icon_fallback)
            allowSwitch.setOnCheckedChangeListener(null)
            allowSwitch.setTrackOnColor(
                ContextCompat.getColor(itemView.context, R.color.notification_manager_amber),
            )
            allowSwitch.isChecked = isAllowed(item.packageName)
            allowSwitch.contentDescription = item.appName
            allowSwitch.setTrackedClickListener(EventUtils.CLICK_ALLOWED_APPS_SWITCH)
            allowSwitch.setOnCheckedChangeListener { _, allowed ->
                onAllowedChanged(item.packageName, allowed)
            }
            itemView.setTrackedClickListener(EventUtils.CLICK_ALLOWED_APPS_ROW) { allowSwitch.toggle() }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AllowedApp>() {
        override fun areItemsTheSame(oldItem: AllowedApp, newItem: AllowedApp) =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AllowedApp, newItem: AllowedApp) = oldItem == newItem
    }
}
