package com.nice.aceclean.ui.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.cleanup.CleanupCategory
import com.nice.aceclean.cleanup.CleanupScanResult
import com.nice.aceclean.databinding.ItemCleanupCategoryBinding
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.EventUtils

internal class CleanupCategoryAdapter(
    private val result: CleanupScanResult,
    private val selected: Set<CleanupCategory>,
    private val onToggle: (CleanupCategory) -> Unit,
) : RecyclerView.Adapter<CleanupCategoryAdapter.Holder>() {
    override fun getItemCount() = CleanupCategory.entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemCleanupCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(CleanupCategory.entries[position])

    internal inner class Holder(private val binding: ItemCleanupCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: CleanupCategory) = with(binding) {
            val context = root.context
            val title = when (category) {
                CleanupCategory.APP_CACHE -> R.string.cache
                CleanupCategory.AD_FILES -> R.string.ad_files
                CleanupCategory.TEMP_FILES -> R.string.temporary_files
                CleanupCategory.APK_FILES -> R.string.obsolete_apk_files
                CleanupCategory.RESIDUAL_FILES -> R.string.residual_files
                CleanupCategory.EMPTY_FILES -> R.string.empty_files
            }
            categoryTitle.setText(title)
            categoryIcon.setImageResource(when (category) {
                CleanupCategory.APP_CACHE, CleanupCategory.APK_FILES -> R.drawable.ic_home_ref_apps
                else -> R.drawable.ic_home_ref_large
            })
            categoryIcon.backgroundTintList = ColorStateList.valueOf(Color.parseColor(when (category.ordinal % 3) {
                0 -> "#F58B00"
                1 -> "#8393FF"
                else -> "#D976EF"
            }))
            val bytes = result.size(category)
            val count = result.candidates(category).size
            categorySize.text = if (bytes == 0L && count > 0) context.getString(R.string.items_found, count)
                else DeviceStats.formatBytes(context, bytes)
            categoryCheck.isSelected = category in selected
            ViewCompat.setAccessibilityDelegate(root, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = CheckBox::class.java.name
                    info.isCheckable = true
                    info.isChecked = category in selected
                }
            })
            root.setTrackedClickListener(EventUtils.CLICK_CLEANUP_CATEGORY) {
                onToggle(category)
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) notifyItemChanged(position)
            }
        }
    }
}
