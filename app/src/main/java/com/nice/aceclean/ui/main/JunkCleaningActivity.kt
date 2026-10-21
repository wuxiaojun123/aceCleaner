package com.nice.aceclean.ui.main

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.cleanup.CleanupCandidate
import com.nice.aceclean.cleanup.CleaningType
import com.nice.aceclean.cleanup.JunkCleaningViewModel
import com.nice.aceclean.databinding.ActivityJunkCleaningBinding
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.similarphoto.SimilarPhotoItem
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.EventUtils
import com.nice.library_ad_sdk.AdSdkManager
import kotlinx.coroutines.launch

/** 共享清理过程页：处理垃圾或相似图片，实际删除成功后进入对应成功页。 */
class JunkCleaningActivity : BaseActivity<ActivityJunkCleaningBinding>(
    { ActivityJunkCleaningBinding.inflate(it) }, R.layout.activity_junk_cleaning,
) {
    private val model: JunkCleaningViewModel by viewModels()
    private var navigated = false
    override val navigationBarColorRes = R.color.color_FFFAF9F7

    private val photoDeleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val approved = result.resultCode == RESULT_OK
        if (model.onPhotoDeleteConsentResult(approved)) {
            EventUtils.track(if (approved) EventUtils.CLICK_DUPLICATES_CONFIRM_DELETE else EventUtils.CLICK_DUPLICATES_CANCEL_DELETE)
        }
    }

    override fun initViews() {
        if (model.type == CleaningType.SIMILAR_PHOTOS) {
            binding.titleView.setText(R.string.dup_title)
            binding.messageView.setText(R.string.string_similar_photos_cleaning)
        }
        AdSdkManager.preloadAsync(applicationContext, AppAdManager.DELETE_SUCCESS)
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
                    if (state.awaitingPhotoConsent && !navigated) requestPhotoDeletion()
                    if (!navigated && (state.result != null || state.unavailable || state.failed || state.cancelled)) {
                        navigated = true
                        val result = state.result
                        if (result != null && result.deletedCount > 0) {
                            setResult(RESULT_OK)
                            if (model.type == CleaningType.SIMILAR_PHOTOS) {
                                if (result.failedCount > 0) Toast.makeText(this@JunkCleaningActivity, R.string.dup_delete_failed, Toast.LENGTH_SHORT).show()
                                DeleteSuccessActivity.show(
                                    this@JunkCleaningActivity, result.deletedCount, result.releasedBytes,
                                    featureIcon = R.drawable.icon_duplicate_photo_cleaner,
                                    source = DeleteSuccessActivity.SOURCE_DUPLICATE,
                                )
                            } else {
                                DeleteSuccessActivity.show(this@JunkCleaningActivity, result.deletedCount, result.releasedBytes)
                            }
                        } else if (state.cancelled) {
                            setResult(RESULT_CANCELED)
                        } else {
                            setResult(RESULT_RESCAN)
                            Toast.makeText(this@JunkCleaningActivity,
                                if (state.unavailable) R.string.string_junk_cleaning_session_expired
                                else if (model.type == CleaningType.SIMILAR_PHOTOS) R.string.dup_delete_failed
                                else R.string.string_junk_cleaning_failed, Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                }
            }
        }
    }

    private fun requestPhotoDeletion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val uris = model.takePhotoDeleteRequest() ?: return
        try {
            val request = MediaStore.createDeleteRequest(contentResolver, uris)
            photoDeleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (error: Exception) {
            model.onPhotoDeleteConsentFailed(error)
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

        fun createSimilarPhotosIntent(context: Context, items: List<SimilarPhotoItem>): Intent =
            Intent(context, JunkCleaningActivity::class.java)
                .putExtra(JunkCleaningViewModel.EXTRA_TYPE, CleaningType.SIMILAR_PHOTOS.name)
                .putExtra(JunkCleaningViewModel.EXTRA_SESSION, JunkCleaningViewModel.enqueueSimilarPhotos(items))
    }
}
