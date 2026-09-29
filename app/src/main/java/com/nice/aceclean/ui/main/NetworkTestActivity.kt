package com.nice.aceclean.ui.main

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ui.widget.CheckmarkAnimationView
import com.nice.aceclean.ui.widget.SpeedTestGaugeView
import com.nice.aceclean.util.NetworkSpeedTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkTestActivity : DeviceToolActivity(R.layout.activity_network_test, R.string.network_test) {

    private var testJob: Job? = null
    private var liveSpeedJob: Job? = null
    @Volatile private var latestLiveSpeedMbps: Double? = null

    override fun initToolViews() {
        view<android.view.View>(R.id.network_test_button).setOnClickListener { startTest() }
    }

    override fun initData() {
        view<android.view.View>(R.id.network_test_button).post { startTest() }
    }

    override fun onDestroy() {
        testJob?.cancel()
        liveSpeedJob?.cancel()
        super.onDestroy()
    }

    private fun startTest() {
        if (testJob?.isActive == true) return
        if (!hasNetwork()) {
            view<TextView>(R.id.network_test_status).setText(R.string.network_unavailable)
            return
        }
        resetResults()
        startLiveSpeedUpdates()
        testJob = lifecycleScope.launch {
            val button = view<TextView>(R.id.network_test_button)
            val status = view<TextView>(R.id.network_test_status)
            val progress = view<SpeedTestGaugeView>(R.id.network_test_progress)
            button.isEnabled = false
            button.setText(R.string.testing)
            runCatching {
                status.setText(R.string.network_test_connecting)
                progress.animateTo(12)
                val ping = withContext(Dispatchers.IO) { NetworkSpeedTest.measurePing() }
                view<TextView>(R.id.network_ping).text = getString(R.string.ping_value, ping)

                status.setText(R.string.network_test_downloading)
                progress.animateTo(38)
                val download = withContext(Dispatchers.IO) {
                    NetworkSpeedTest.measureDownloadMbps(::recordLiveSpeed)
                }
                view<TextView>(R.id.network_download).text = getString(R.string.speed_value, download)

                status.setText(R.string.network_test_uploading)
                progress.animateTo(72)
                val upload = withContext(Dispatchers.IO) {
                    NetworkSpeedTest.measureUploadMbps(::recordLiveSpeed)
                }
                view<TextView>(R.id.network_upload).text = getString(R.string.speed_value, upload)

                status.setText(R.string.network_test_complete)
                liveSpeedJob?.cancel()
                progress.animateTo(100, ::showSuccessAnimation)
            }.onFailure {
                liveSpeedJob?.cancel()
                progress.animateTo(0)
                status.setText(R.string.network_test_failed)
            }
            button.isEnabled = true
            button.setText(R.string.test_again)
        }
    }

    private fun resetResults() {
        view<View>(R.id.network_live_speed_group).visibility = View.VISIBLE
        view<CheckmarkAnimationView>(R.id.network_test_success).visibility = View.GONE
        view<TextView>(R.id.network_live_speed).text = "0.0"
        latestLiveSpeedMbps = null
        view<TextView>(R.id.network_ping).setText(R.string.ping_placeholder)
        view<TextView>(R.id.network_download).setText(R.string.speed_placeholder)
        view<TextView>(R.id.network_upload).setText(R.string.speed_placeholder)
        view<SpeedTestGaugeView>(R.id.network_test_progress).progress = 0
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
                    view<TextView>(R.id.network_live_speed).text =
                        String.format(java.util.Locale.US, "%.1f", speed)
                }
            }
        }
    }

    private fun showSuccessAnimation() {
        view<View>(R.id.network_live_speed_group).visibility = View.GONE
        view<CheckmarkAnimationView>(R.id.network_test_success).apply {
            visibility = View.VISIBLE
            play()
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
