package com.nice.aceclean.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.nice.aceclean.R
import com.nice.aceclean.ui.widget.IosSwitchView

data class AllowedApp(val packageName: String, val appName: String)

class AllowedAppsAdapter(
    private val isAllowed: (String) -> Boolean,
    private val onAllowedChanged: (String, Boolean) -> Unit,
) : ListAdapter<AllowedApp, AllowedAppsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_allowed_app, parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<ImageView>(R.id.allowed_app_icon)
        private val fallback = view.findViewById<TextView>(R.id.allowed_app_icon_fallback)
        private val name = view.findViewById<TextView>(R.id.allowed_app_name)
        private val packageName = view.findViewById<TextView>(R.id.allowed_app_package)
        private val allowSwitch = view.findViewById<IosSwitchView>(R.id.allowed_app_switch)

        fun bind(item: AllowedApp) {
            name.text = item.appName
            packageName.text = item.packageName
            val drawable = AppIconLoader.load(itemView.context, item.packageName)
            icon.visibility = if (drawable != null) View.VISIBLE else View.GONE
            fallback.visibility = if (drawable == null) View.VISIBLE else View.GONE
            icon.setImageDrawable(drawable)
            fallback.text = item.appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            allowSwitch.setOnCheckedChangeListener(null)
            allowSwitch.setTrackOnColor(
                ContextCompat.getColor(itemView.context, R.color.notification_manager_amber),
            )
            allowSwitch.isChecked = isAllowed(item.packageName)
            allowSwitch.contentDescription = item.appName
            allowSwitch.setOnCheckedChangeListener { _, allowed ->
                onAllowedChanged(item.packageName, allowed)
            }
            itemView.setOnClickListener { allowSwitch.toggle() }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AllowedApp>() {
        override fun areItemsTheSame(oldItem: AllowedApp, newItem: AllowedApp) =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AllowedApp, newItem: AllowedApp) = oldItem == newItem
    }
}
