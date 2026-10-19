package com.nice.aceclean.ui.main

import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.viewbinding.ViewBinding
import com.nice.aceclean.ui.base.BaseActivity

enum class StorageCleanerType { SCREENSHOT, BIG_FILE, VIDEO }

abstract class StorageCleanerActivity<B : ViewBinding>(
    cleanerType: StorageCleanerType,
    inflateResult: (LayoutInflater) -> B,
    resultLayout: Int,
) : BaseActivity<B>(inflateResult, resultLayout) {
    private val handoff: FeatureResultViewModel by viewModels()
    protected val resultBinding get() = binding
    protected var isResultShown = false
        private set
    protected lateinit var initialScanResult: FeatureScanResult
        private set
    private val feature = when (cleanerType) {
        StorageCleanerType.SCREENSHOT -> ScanFeature.SCREENSHOT
        StorageCleanerType.BIG_FILE -> ScanFeature.BIG_FILE
        StorageCleanerType.VIDEO -> ScanFeature.VIDEO
    }

    final override fun initViews() {
        enableBackInterstitial()
        initialScanResult = scanResult(handoff, feature) ?: return
        isResultShown = true
        initResultViews()
    }

    protected fun updateScanResult(result: FeatureScanResult) { handoff.update(result) }
    protected abstract fun initResultViews()
}
