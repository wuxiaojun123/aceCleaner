package com.nice.aceclean.ui.adapter

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.databinding.OnboardingItemFirstJunkCategoryBinding
import com.nice.aceclean.databinding.OnboardingItemFirstJunkChildBinding
import com.nice.aceclean.ui.onboarding.FirstRunJunkCategory
import com.nice.aceclean.ui.onboarding.FirstRunJunkGroup
import com.nice.aceclean.ui.onboarding.FirstRunJunkItem
import com.nice.aceclean.ui.onboarding.FirstRunJunkResult

internal class OnboardingJunkAdapter(
    private val result: FirstRunJunkResult,
    private val onChanged: () -> Unit,
) : RecyclerView.Adapter<OnboardingJunkAdapter.Holder>() {
    override fun getItemCount() = result.groups.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        OnboardingItemFirstJunkCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(result.groups[position])

    inner class Holder(private val binding: OnboardingItemFirstJunkCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(group: FirstRunJunkGroup) {
            val context = binding.root.context
            binding.categoryName.setText(when (group.category) {
                FirstRunJunkCategory.SYSTEM_CACHE -> R.string.string_onboarding_first_junk_system_cache
                FirstRunJunkCategory.THUMBNAILS -> R.string.string_onboarding_first_junk_thumbnails
                FirstRunJunkCategory.EMPTY_FOLDERS -> R.string.string_onboarding_first_junk_empty_folders
                FirstRunJunkCategory.DOWNLOADS -> R.string.string_onboarding_first_junk_downloads
                FirstRunJunkCategory.APKS -> R.string.string_onboarding_first_junk_apks
            })
            binding.categorySize.text = Formatter.formatShortFileSize(context, group.bytes)
            binding.expandView.visibility = if (group.items.isNotEmpty()) View.VISIBLE else View.INVISIBLE
            binding.expandView.rotation = if (group.expanded) 180f else 0f
            check(binding.categoryCheck, group.allSelected)
            binding.childContainer.apply {
                visibility = if (group.expanded && group.items.isNotEmpty()) View.VISIBLE else View.GONE
                layoutManager = LinearLayoutManager(context)
                adapter = Children(group.items) {
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION) notifyItemChanged(bindingAdapterPosition)
                    onChanged()
                }
            }
            binding.categoryHeader.setOnClickListener {
                group.expanded = !group.expanded
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) notifyItemChanged(bindingAdapterPosition)
            }
            binding.categoryCheck.setOnClickListener {
                val selected = !group.allSelected
                group.items.forEach { it.selected = selected }
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) notifyItemChanged(bindingAdapterPosition)
                onChanged()
            }
        }
    }

    private class Children(val items: List<FirstRunJunkItem>, val onChanged: () -> Unit) : RecyclerView.Adapter<Children.Holder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            OnboardingItemFirstJunkChildBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
        inner class Holder(private val binding: OnboardingItemFirstJunkChildBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: FirstRunJunkItem) {
                binding.itemName.text = item.name
                binding.itemSize.text = Formatter.formatShortFileSize(binding.root.context, item.bytes)
                check(binding.itemCheck, item.selected)
                binding.root.setOnClickListener { item.selected = !item.selected; onChanged() }
            }
        }
    }

    companion object {
        private fun check(view: TextView, selected: Boolean) {
            view.setBackgroundResource(if (selected) R.drawable.onboarding_bg_first_junk_check_selected else R.drawable.onboarding_bg_first_junk_check_unselected)
            view.text = if (selected) view.context.getString(R.string.string_onboarding_literal_5) else ""
        }
    }
}