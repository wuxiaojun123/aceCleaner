package com.nice.aceclean.ui.main

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.DeviceStats

/** Shared completion screen displayed after files have actually been removed. */
class DeleteSuccessActivity : BaseActivity(R.layout.activity_delete_success) {

    override fun initViews() {
        val removedCount = intent.getIntExtra(EXTRA_REMOVED_COUNT, 0)
        val freedBytes = intent.getLongExtra(EXTRA_FREED_BYTES, 0L)
        val featureIcon = intent.getIntExtra(EXTRA_FEATURE_ICON, R.drawable.ic_competitor_success)
        val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()

        findViewById<TextView>(R.id.delete_success_title).text = featureTitle(source)
        findViewById<ImageView>(R.id.delete_success_feature_icon).setImageResource(featureIcon)
        findViewById<View>(R.id.delete_success_try_now).setOnClickListener {
            startActivity(Intent(this, NotificationCleanerActivity::class.java))
            finish()
        }
        bindTool(R.id.delete_success_notification, NotificationCleanerActivity::class.java)
        bindTool(R.id.delete_success_apps, AppManagerActivity::class.java)
        bindTool(R.id.delete_success_network, NetworkTrafficActivity::class.java)
        bindTool(R.id.delete_success_cleanup, CleanUpActivity::class.java)
        bindTool(R.id.delete_success_screenshot, ScreenshotCleanerActivity::class.java)
        findViewById<View>(R.id.delete_success_back).setOnClickListener { returnToHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnToHome()
        })
    }

    private fun returnToHome() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        finish()
    }

    private fun bindTool(cardId: Int, activityClass: Class<out BaseActivity>) {
        findViewById<View>(cardId).setOnClickListener {
            startActivity(Intent(this, activityClass))
            finish()
        }
    }

    private fun featureTitle(source: String) = when (source) {
        SOURCE_SCREENSHOT -> getString(R.string.screenshot_cleaner)
        SOURCE_BIG_FILE -> getString(R.string.big_file_cleaner)
        SOURCE_VIDEO -> getString(R.string.video_cleaner)
        SOURCE_DUPLICATE -> getString(R.string.duplicate_photo_cleaner)
        else -> getString(R.string.clean_up)
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

        fun show(
            context: Context,
            removedCount: Int,
            freedBytes: Long,
            featureIcon: Int = R.drawable.ic_competitor_success,
            source: String = SOURCE_CLEAN_UP,
        ) {
            if (removedCount <= 0) return
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
