package com.nice.aceclean.ui.main

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.databinding.ActivityNotificationCleaningBinding
import com.nice.aceclean.ui.base.BaseActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NotificationCleaningSuccessActivity : BaseActivity<ActivityNotificationCleaningBinding>({ ActivityNotificationCleaningBinding.inflate(it) }, R.layout.activity_notification_cleaning) {
    override fun initViews() {
        lifecycleScope.launch {
            delay(1_200L)
            binding.notificationCleaningProgress.visibility = View.GONE
            binding.notificationCleaningSuccessIcon.visibility = View.VISIBLE
            binding.notificationCleaningTitle.setText(R.string.notif_clear_success_title)
            binding.notificationCleaningSubtitle.setText(R.string.notif_clear_success_subtitle)
            delay(1_200L)
            startActivity(
                Intent(this@NotificationCleaningSuccessActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    companion object {
        private const val EXTRA_CLEARED_COUNT = "cleared_count"

        fun createIntent(context: Context, clearedCount: Int): Intent =
            Intent(context, NotificationCleaningSuccessActivity::class.java)
                .putExtra(EXTRA_CLEARED_COUNT, clearedCount)
    }
}
