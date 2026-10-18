package com.nice.aceclean.ui.main

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import com.nice.aceclean.R
import com.nice.aceclean.ad.AppAdManager
import com.nice.aceclean.ad.HotStartManager
import com.nice.aceclean.databinding.ActivityFeatureScanBinding
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.LogUtils
import com.nice.library_ad_sdk.AdSdkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class FeatureScanActivity : BaseActivity<ActivityFeatureScanBinding>(
    { ActivityFeatureScanBinding.inflate(it) }, R.layout.activity_feature_scan,
) {
    private val model: FeatureScanViewModel by viewModels()
    private val feature get() = ScanFeature.from(intent)
    private var transitionRunning = false
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        model.permissionRequested = false
        if (result.resultCode == Activity.RESULT_OK && feature?.hasPermission(this) == true) {
            beginScan()
        } else finish()
    }

    override fun initViews() {
        val target = feature ?: return finish()
        val restored = savedStateRegistry.consumeRestoredStateForKey("feature_scan")
        if (restored?.getBoolean("permission_requested") == true) model.permissionRequested = true
        savedStateRegistry.registerSavedStateProvider("feature_scan") {
            android.os.Bundle().apply { putBoolean("permission_requested", model.permissionRequested) }
        }
        binding.scanTitle.setText(target.title)
        binding.scanAnimation.setAnimation(target.animation)
        binding.scanBack.setOnClickListener { if (!transitionRunning) finish() }
        binding.scanRetry.setOnClickListener {
            binding.scanRetry.visibility = View.GONE
            binding.scanStatus.setText(R.string.scanning)
            val result = model.state.value.result
            if (result != null && !transitionRunning) {
                transitionRunning = true
                lifecycleScope.launch { completeScan(target, result) }
            } else checkPermission()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!transitionRunning) finish() }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect { state ->
                    state.files?.let { binding.scanDetail.text = getString(R.string.scanned_files_progress, it) }
                    if (state.failed) {
                        binding.scanAnimation.pauseAnimation()
                        binding.scanStatus.setText(R.string.string_feature_scan_failed)
                        binding.scanRetry.visibility = View.VISIBLE
                    } else if (state.result != null && !model.navigated && !transitionRunning) {
                        transitionRunning = true
                        lifecycleScope.launch { completeScan(target, state.result) }
                    }
                }
            }
        }
        checkPermission()
    }

    private fun checkPermission() {
        val target = feature ?: return
        if (target.hasPermission(this)) beginScan()
        else if (!model.permissionRequested) {
            model.permissionRequested = true
            permissionLauncher.launch(Intent(this, FeatureScanPermissionActivity::class.java)
                .putExtra(ScanFeature.EXTRA_FEATURE, target.name))
        }
    }

    private fun beginScan() {
        val target = feature ?: return
        binding.scanAnimation.playAnimation()
        AdSdkManager.preloadAsync(applicationContext, AppAdManager.FEATURE_SCAN_COMPLETE)
        model.scan(target)
    }

    private suspend fun completeScan(target: ScanFeature, result: FeatureScanResult) {
        transitionRunning = true
        binding.scanStatus.setText(R.string.string_feature_scan_complete)
        binding.scanAnimation.pauseAnimation()
        try {
            if (!target.hasPermission(this)) {
                finish()
                return
            }
            if (!model.adAttempted) {
                model.adAttempted = true
                LogUtils.d("scan_ad_attempt feature=$target placement=${AppAdManager.FEATURE_SCAN_COMPLETE}", "FeatureScan")
                val loaded = AdSdkManager.preload(applicationContext, AppAdManager.FEATURE_SCAN_COMPLETE)
                lifecycle.withResumed { }
                var shown = false
                if (loaded) {
                    val previousSkip = HotStartManager.skipNextHotStart
                    HotStartManager.markSkipNextHotStart()
                    try {
                        shown = AppAdManager.showLoaded(this, AppAdManager.FEATURE_SCAN_COMPLETE)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        LogUtils.w("scan_ad_failed feature=$target", "FeatureScan", error)
                    } finally {
                        HotStartManager.skipNextHotStart = previousSkip
                    }
                }
                LogUtils.d("scan_ad_finished feature=$target loaded=$loaded shown=$shown", "FeatureScan")
            }
            lifecycle.withResumed {
                if (!model.navigated && !isFinishing && !isDestroyed) {
                    val id = FeatureScanResults.put(result)
                    startActivity(Intent(this, target.destination).putExtra(ScanFeature.EXTRA_RESULT, id))
                    model.navigated = true
                    LogUtils.d("scan_result_open feature=$target", "FeatureScan")
                    finish()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LogUtils.e("scan_transition_failed feature=$target", "FeatureScan", error)
            // Allow retry without repeating the completed scan or an ad attempt.
            binding.scanStatus.setText(R.string.string_feature_scan_failed)
            binding.scanRetry.visibility = View.VISIBLE
        } finally {
            transitionRunning = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (!model.state.value.failed && model.state.value.result == null) binding.scanAnimation.resumeAnimation()
    }
    override fun onStop() {
        binding.scanAnimation.pauseAnimation()
        super.onStop()
    }
}
