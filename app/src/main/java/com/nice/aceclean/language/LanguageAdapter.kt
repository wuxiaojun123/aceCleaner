package com.nice.aceclean.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import java.util.Locale

data class LanguageItem(
    @param:StringRes val nameResId: Int,
    @param:DrawableRes val iconResId: Int,
    val locale: Locale,
)

class LanguageAdapter(
    private val items: List<LanguageItem>,
    selectedLocale: Locale,
    private val onLanguageSelected: (LanguageItem) -> Unit,
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    private var selectedTag = selectedLocale.toLanguageTag()
    private var interactionsEnabled = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false)
        return LanguageViewHolder(view)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(items[position], animateSelection = false)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION_ANIMATION)) {
            holder.bind(items[position], animateSelection = true)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateSelected(locale: Locale) {
        val oldTag = selectedTag
        selectedTag = locale.toLanguageTag()
        val oldIndex = items.indexOfFirst { it.locale.toLanguageTag() == oldTag }
        val newIndex = items.indexOfFirst { it.locale.toLanguageTag() == selectedTag }
        if (oldIndex != -1 && oldIndex != newIndex) notifyItemChanged(oldIndex)
        if (newIndex != -1) notifyItemChanged(newIndex, PAYLOAD_SELECTION_ANIMATION)
    }

    fun setInteractionsEnabled(enabled: Boolean) {
        interactionsEnabled = enabled
    }

    inner class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val languageIcon: ImageView = itemView.findViewById(R.id.language_icon)
        private val languageName: TextView = itemView.findViewById(R.id.language_name)
        private val selectedIndicator: View = itemView.findViewById(R.id.selected_indicator)

        fun bind(item: LanguageItem, animateSelection: Boolean) {
            val selected = item.locale.toLanguageTag() == selectedTag
            itemView.animate().cancel()
            selectedIndicator.animate().cancel()
            languageIcon.setImageResource(item.iconResId)
            languageName.setText(item.nameResId)
            selectedIndicator.visibility = if (selected) View.VISIBLE else View.GONE
            selectedIndicator.alpha = 1f
            selectedIndicator.scaleX = 1f
            selectedIndicator.scaleY = 1f
            itemView.scaleX = 1f
            itemView.scaleY = 1f
            itemView.setBackgroundResource(
                if (selected) R.drawable.bg_language_row_selected else R.drawable.bg_language_row,
            )
            itemView.setOnClickListener {
                if (interactionsEnabled) onLanguageSelected(item)
            }
            if (selected && animateSelection) playSelectionAnimation()
        }

        private fun playSelectionAnimation() {
            itemView.scaleX = 0.985f
            itemView.scaleY = 0.985f
            itemView.animate().scaleX(1f).scaleY(1f).setDuration(220L).start()
            selectedIndicator.alpha = 0f
            selectedIndicator.scaleX = 0.65f
            selectedIndicator.scaleY = 0.65f
            selectedIndicator.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260L).start()
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION_ANIMATION = "selection_animation"
    }
}
