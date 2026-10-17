package com.nice.aceclean.ui.main

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nice.aceclean.R
import com.nice.aceclean.cleanup.CleanupCandidate
import com.nice.aceclean.cleanup.JunkCleaningViewModel
import com.nice.aceclean.databinding.ActivityJunkCleaningBinding
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.DeviceStats
import kotlinx.coroutines.launch

/** 垃圾清理过程页：循环播放吸尘器动画，真实清理完成后进入成功引导。 */
class JunkCleaningActivity : BaseActivity<ActivityJunkCleaningBinding>(
    { ActivityJunkCleaningBinding.inflate(it) }, R.layout.activity_junk_cleaning,
) {
    private val model: JunkCleaningViewModel by viewModels()
    private var navigated = false
    override val navigationBarColorRes = R.color.color_FFFAF9F7

    override fun initViews() {
        // 清理期间防止返回造成用户重复提交同一批文件。
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect { state ->
                    binding.cleanedSize.text = DeviceStats.formatBytes(this@JunkCleaningActivity, state.releasedBytes)
                    binding.progressView.text = getString(R.string.percentage_value, state.progress)
                    binding.progressBar.progress = state.progress
                    if (!navigated && (state.result != null || state.unavailable || state.failed)) {
                        navigated = true
                        val result = state.result
                        if (result != null && result.deletedCount > 0) {
                            setResult(RESULT_OK)
                            DeleteSuccessActivity.show(this@JunkCleaningActivity, result.deletedCount, result.releasedBytes)
                        } else {
                            setResult(RESULT_RESCAN)
                            Toast.makeText(this@JunkCleaningActivity,
                                if (state.unavailable) R.string.string_junk_cleaning_session_expired
                                else R.string.string_junk_cleaning_failed, Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.cleanAnimation.resumeAnimation()
    }

    override fun onPause() {
        binding.cleanAnimation.pauseAnimation()
        super.onPause()
    }

    companion object {
        const val RESULT_RESCAN = RESULT_FIRST_USER
        fun createIntent(context: Context, candidates: List<CleanupCandidate>): Intent =
            Intent(context, JunkCleaningActivity::class.java).putExtra(
                JunkCleaningViewModel.EXTRA_SESSION, JunkCleaningViewModel.enqueue(candidates),
            )
    }
}
