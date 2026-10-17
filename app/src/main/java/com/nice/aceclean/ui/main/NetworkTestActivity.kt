package com.nice.aceclean.ui.main

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.databinding.ActivityNetworkTestBinding
import com.nice.aceclean.util.NetworkSpeedTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkTestActivity : DeviceToolActivity<ActivityNetworkTestBinding>({ ActivityNetworkTestBinding.inflate(it) }, R.layout.activity_network_test, R.string.network_test) {

    private var testJob: Job? = null
    private var liveSpeedJob: Job? = null
    @Volatile private var latestLiveSpeedMbps: Double? = null

    override val featureHeader get() = binding.featureHeader
    override val navigationBarColorRes get() = R.color.color_FFFAFBF9

    override fun initToolViews() {
        binding.featureHeader.featureTitle.apply {
            textSize = 19f
            setTextColor(ContextCompat.getColor(context, R.color.color_FF252A31))
            layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = (52 * resources.displayMetrics.density).toInt()
            }
        }
        binding.networkTestButton.setOnClickListener { startTest() }
    }

    override fun initData() {
        binding.networkTestButton.post { startTest() }
    }

    override fun onDestroy() {
        testJob?.cancel()
        liveSpeedJob?.cancel()
        super.onDestroy()
    }

    private fun startTest() {
        if (testJob?.isActive == true) return
        if (!hasNetwork()) {
            binding.networkTestStatus.setText(R.string.network_unavailable)
            return
        }
        resetResults()
        startLiveSpeedUpdates()
        testJob = lifecycleScope.launch {
            val button = binding.networkTestButton
            val status = binding.networkTestStatus
            val progress = binding.networkTestProgress
            button.isEnabled = false
            button.setText(R.string.testing)
            runCatching {
                status.setText(R.string.network_test_connecting)
                showTestStage(0)
                progress.animateTo(12)
                val ping = withContext(Dispatchers.IO) { NetworkSpeedTest.measurePing() }
                binding.networkPing.text = getString(R.string.string_network_ping_number, ping)

                status.setText(R.string.network_test_downloading)
                showTestStage(1)
                progress.animateTo(38)
                val download = withContext(Dispatchers.IO) {
                    NetworkSpeedTest.measureDownloadMbps(::recordLiveSpeed)
                }
                binding.networkDownload.text = getString(R.string.string_network_speed_number, download)

                status.setText(R.string.network_test_uploading)
                showTestStage(2)
                progress.animateTo(72)
                val upload = withContext(Dispatchers.IO) {
                    NetworkSpeedTest.measureUploadMbps(::recordLiveSpeed)
                }
                binding.networkUpload.text = getString(R.string.string_network_speed_number, upload)

                status.setText(R.string.network_test_complete)
                showTestStage(3)
                binding.networkResultsHint.setText(R.string.string_network_results_complete)
                liveSpeedJob?.cancel()
                progress.animateTo(100, ::showSuccessAnimation)
            }.onFailure {
                liveSpeedJob?.cancel()
                progress.animateTo(0)
                status.setText(R.string.network_test_failed)
                showTestStage(-1)
            }
            button.isEnabled = true
            button.setText(R.string.test_again)
        }
    }

    private fun resetResults() {
        binding.networkLiveSpeedGroup.visibility = View.VISIBLE
        binding.networkTestSuccess.visibility = View.GONE
        binding.networkTestSuccessGroup.visibility = View.GONE
        binding.networkLiveSpeed.setText(R.string.string_network_zero)
        latestLiveSpeedMbps = null
        binding.networkPing.setText(R.string.string_network_number_placeholder)
        binding.networkDownload.setText(R.string.string_network_number_placeholder)
        binding.networkUpload.setText(R.string.string_network_number_placeholder)
        binding.networkResultsHint.setText(R.string.string_network_results_hint)
        showTestStage(-1)
        binding.networkTestProgress.progress = 0
    }

    private fun recordLiveSpeed(speedMbps: Double) {
        latestLiveSpeedMbps = speedMbps
    }

    private fun startLiveSpeedUpdates() {
        liveSpeedJob?.cancel()
        liveSpeedJob = lifecycleScope.launch {
            while (isActive) {
                delay(LIVE_SPEED_REFRESH_MILLIS)
                latestLiveSpeedMbps?.let { speed ->
                    binding.networkLiveSpeed.text =
                        String.format(java.util.Locale.US, "%.1f", speed)
                }
            }
        }
    }

    private fun showSuccessAnimation() {
        binding.networkLiveSpeedGroup.visibility = View.GONE
        binding.networkTestSuccessGroup.visibility = View.VISIBLE
        binding.networkTestSuccess.apply {
            visibility = View.VISIBLE
            play()
        }
    }

    /** Presentation only: the existing test sequence determines the highlighted stage. */
    private fun showTestStage(stage: Int) {
        val steps = listOf(binding.networkStepPing, binding.networkStepDownload, binding.networkStepUpload)
        val icons = listOf(binding.networkPingIcon, binding.networkDownloadIcon, binding.networkUploadIcon)
        steps.forEachIndexed { index, step ->
            step.isSelected = index == stage
            step.isActivated = index < stage
            icons[index].setColorFilter(
                ContextCompat.getColor(
                    this,
                    if (index == stage) R.color.color_FFEE6229 else R.color.color_FF727981,
                ),
            )
        }
    }

    private fun hasNetwork(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val LIVE_SPEED_REFRESH_MILLIS = 1_000L
    }
}
