package com.nice.aceclean.ui.main

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.ActivityDeleteSuccessBinding
import com.nice.aceclean.databinding.ItemDeleteSuccessFeatureBinding
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.EventUtils

/** Shared completion screen displayed after files or apps have actually been removed. */
class DeleteSuccessActivity : BaseActivity<ActivityDeleteSuccessBinding>(
    { ActivityDeleteSuccessBinding.inflate(it) },
    R.layout.activity_delete_success,
) {

    override fun initViews() {
        val current = Feature.fromSource(intent.getStringExtra(EXTRA_SOURCE))
        val recommended = current.recommended
        val removedCount = intent.getIntExtra(EXTRA_REMOVED_COUNT, 0).coerceAtLeast(0)
        val freedBytes = intent.getLongExtra(EXTRA_FREED_BYTES, 0L).coerceAtLeast(0L)

        binding.deleteSuccessTitle.setText(current.titleRes)
        val resultMessageRes = if (current == Feature.APP_MANAGER) {
            R.string.string_delete_success_apps_uninstalled
        } else {
            R.string.clean_success_files
        }
        binding.deleteSuccessResultMessage.text = getString(resultMessageRes, removedCount).let { result ->
            if (freedBytes > 0L) {
                result + "\n" + getString(
                    R.string.string_delete_success_space_freed,
                    DeviceStats.formatBytes(this, freedBytes),
                )
            } else {
                result
            }
        }
        binding.deleteSuccessNextIcon.setImageResource(recommended.topIconRes)
        val recommendedName = getString(recommended.titleRes)
        val prompt = getString(R.string.string_delete_success_continue_with, recommendedName)
        binding.deleteSuccessRecommendedPrompt.text = SpannableString(prompt).apply {
            val start = prompt.indexOf(recommendedName)
            if (start >= 0) {
                setSpan(
                    ForegroundColorSpan(getColor(R.color.color_FF42B8FF)),
                    start,
                    start + recommendedName.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }

        binding.deleteSuccessOtherFeatures.layoutManager = LinearLayoutManager(this)
        binding.deleteSuccessOtherFeatures.adapter = FeatureAdapter(
            Feature.entries.filterNot { it == current },
            ::openFeature,
        )
        binding.deleteSuccessBack.setTrackedClickListener(EventUtils.CLICK_DELETE_SUCCESS_BACK) { returnToHome() }
        binding.deleteSuccessTryNow.setTrackedClickListener(EventUtils.CLICK_DELETE_SUCCESS_TRY) { openFeature(recommended) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnToHome()
        })
    }

    private fun openFeature(feature: Feature) {
        startActivity(Intent(this, feature.destination))
        finish()
    }

    private fun returnToHome() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        finish()
    }

    private enum class Feature(
        val source: String,
        @param:StringRes val titleRes: Int,
        @param:StringRes val descriptionRes: Int,
        @param:DrawableRes val iconRes: Int,
        @param:DrawableRes val topIconRes: Int,
        val destination: Class<out BaseActivity<*>>,
    ) {
        CLEAN_UP(
            SOURCE_CLEAN_UP, R.string.clean_up, R.string.clean_success_message,
            R.drawable.hkep_ic_one_click_finish, R.drawable.hkep_ic_one_click_finish_top,
            CleanUpActivity::class.java,
        ),
        SCREENSHOT(
            SOURCE_SCREENSHOT, R.string.screenshot_cleaner, R.string.clean_success_screenshot_hint,
            R.drawable.hkep_ic_picture_finish, R.drawable.hkep_ic_picture_finish_top,
            ScreenshotCleanerActivity::class.java,
        ),
        BIG_FILE(
            SOURCE_BIG_FILE, R.string.big_file_cleaner, R.string.clean_success_big_file_hint,
            R.drawable.hkep_ic_large_files_finish, R.drawable.hkep_ic_large_files_finish_top,
            BigFileCleanerActivity::class.java,
        ),
        VIDEO(
            SOURCE_VIDEO, R.string.video_cleaner, R.string.string_delete_success_video_hint,
            R.drawable.icon_video_cleaner, R.drawable.icon_video_cleaner,
            VideoCleanerActivity::class.java,
        ),
        DUPLICATE(
            SOURCE_DUPLICATE, R.string.duplicate_photo_cleaner, R.string.clean_success_duplicate_hint,
            R.drawable.hkep_icon_duplicate_photo_cleaner_finish,
            R.drawable.hkep_icon_duplicate_photo_cleaner_finish_top,
            SimilarPhotoCleanerActivity::class.java,
        ),
        APP_MANAGER(
            SOURCE_APP_MANAGER, R.string.app_manager, R.string.uninstall_unused_apps,
            R.drawable.icon_app_manager, R.drawable.icon_app_manager,
            AppManagerActivity::class.java,
        );

        val recommended: Feature
            get() = when (this) {
                CLEAN_UP -> SCREENSHOT
                SCREENSHOT -> DUPLICATE
                BIG_FILE -> VIDEO
                VIDEO -> SCREENSHOT
                DUPLICATE -> BIG_FILE
                APP_MANAGER -> CLEAN_UP
            }

        companion object {
            fun fromSource(source: String?): Feature = entries.firstOrNull { it.source == source } ?: CLEAN_UP
        }
    }

    private class FeatureAdapter(
        private val features: List<Feature>,
        private val onClick: (Feature) -> Unit,
    ) : RecyclerView.Adapter<FeatureAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemDeleteSuccessFeatureBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val feature = features[position]
            holder.binding.featureIcon.setImageResource(feature.iconRes)
            holder.binding.featureTitle.setText(feature.titleRes)
            holder.binding.featureDescription.setText(feature.descriptionRes)
            holder.binding.root.setTrackedClickListener(EventUtils.CLICK_DELETE_SUCCESS_FEATURE) { onClick(feature) }
        }

        override fun getItemCount(): Int = features.size

        class Holder(val binding: ItemDeleteSuccessFeatureBinding) : RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        private const val EXTRA_REMOVED_COUNT = "removed_count"
        private const val EXTRA_FREED_BYTES = "freed_bytes"
        private const val EXTRA_FEATURE_ICON = "feature_icon"
        private const val EXTRA_SOURCE = "source"
        const val SOURCE_CLEAN_UP = "clean_up"
        const val SOURCE_SCREENSHOT = "screenshot"
        const val SOURCE_BIG_FILE = "big_file"
        const val SOURCE_VIDEO = "video"
        const val SOURCE_DUPLICATE = "duplicate"
        const val SOURCE_APP_MANAGER = "app_manager"

        fun show(
            context: Context,
            removedCount: Int,
            freedBytes: Long,
            featureIcon: Int = R.drawable.ic_competitor_success,
            source: String = SOURCE_CLEAN_UP,
            allowEmptyResult: Boolean = false,
        ) {
            if (removedCount <= 0 && !allowEmptyResult) return
            context.startActivity(
                Intent(context, DeleteSuccessActivity::class.java).apply {
                    putExtra(EXTRA_REMOVED_COUNT, removedCount)
                    putExtra(EXTRA_FREED_BYTES, freedBytes)
                    putExtra(EXTRA_FEATURE_ICON, featureIcon)
                    putExtra(EXTRA_SOURCE, source)
                },
            )
        }
    }
}
