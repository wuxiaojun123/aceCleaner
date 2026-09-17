package com.nice.aceclean.ui.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R

data class DeviceScanStep(
    @param:DrawableRes val iconRes: Int,
    val title: String,
    val value: String,
    val complete: Boolean = false,
)

class DeviceScanStepAdapter(
    private val steps: MutableList<DeviceScanStep>,
) : RecyclerView.Adapter<DeviceScanStepAdapter.StepViewHolder>() {

    fun complete(position: Int, value: String) {
        steps[position] = steps[position].copy(value = value, complete = true)
        notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device_scan_step, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(steps[position], position == steps.lastIndex)
    }

    override fun onViewRecycled(holder: StepViewHolder) {
        holder.clearAnimation()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = steps.size

    class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.guide_step_icon)
        private val title: TextView = itemView.findViewById(R.id.guide_step_title)
        private val value: TextView = itemView.findViewById(R.id.guide_step_value)
        private val status: ImageView = itemView.findViewById(R.id.guide_step_status)
        private val divider: View = itemView.findViewById(R.id.guide_step_divider)

        fun bind(step: DeviceScanStep, isLast: Boolean) {
            icon.setImageResource(step.iconRes)
            title.text = step.title
            value.text = step.value
            divider.visibility = if (isLast) View.GONE else View.VISIBLE
            status.clearAnimation()
            if (step.complete) {
                status.setImageResource(R.drawable.ic_guide_complete)
            } else {
                status.setImageResource(R.drawable.guide_loading)
                status.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.guide_loading_rotate))
            }
        }

        fun clearAnimation() = status.clearAnimation()
    }
}
