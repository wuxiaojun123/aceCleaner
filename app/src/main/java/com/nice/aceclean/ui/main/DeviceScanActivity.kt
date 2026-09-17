package com.nice.aceclean.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.ui.onboarding.DeviceScanStep
import com.nice.aceclean.ui.onboarding.DeviceScanStepAdapter
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.NetworkSpeedSampler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DeviceScanActivity : BaseActivity(R.layout.activity_device_scan) {

    override val statusBarColorRes: Int = R.color.white

    private lateinit var stepsView: RecyclerView
    private lateinit var startButton: TextView
    private lateinit var adapter: DeviceScanStepAdapter

    override fun initViews() {
        stepsView = view(R.id.guide_steps)
        startButton = view(R.id.guide_start)

        adapter = DeviceScanStepAdapter(initialSteps().toMutableList())
        stepsView.layoutManager = LinearLayoutManager(this)
        stepsView.adapter = adapter
        stepsView.itemAnimator = null

        startButton.setOnClickListener {
            markComplete(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
    }

    override fun initData() {
        lifecycleScope.launch {
            scanValues().forEachIndexed { index, value ->
                delay(STEP_DELAY_MS)
                adapter.complete(index, value)
                stepsView.smoothScrollToPosition(index)
            }
            startButton.isEnabled = true
        }
    }

    private fun initialSteps(): List<DeviceScanStep> {
        val loading = getString(R.string.guide_loading)
        return listOf(
            DeviceScanStep(R.drawable.guide_device, getString(R.string.guide_device), loading),
            DeviceScanStep(R.drawable.guide_system, getString(R.string.guide_system), loading),
            DeviceScanStep(R.drawable.guide_screen, getString(R.string.guide_screen), loading),
            DeviceScanStep(R.drawable.guide_density, getString(R.string.guide_density), loading),
            DeviceScanStep(R.drawable.guide_storage, getString(R.string.guide_storage), loading),
            DeviceScanStep(R.drawable.guide_engine, getString(R.string.guide_engine), loading),
        )
    }

    @Suppress("DEPRECATION")
    private fun scanValues(): List<String> {
        val point = Point().also {
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(it)
        }
        val storage = DeviceStats.storageSnapshot(this)
        val model = Build.MODEL.orEmpty().trim().replace(Regex("\\s+"), "")
        return listOf(
            "${Build.MANUFACTURER} $model".trim(),
            getString(R.string.guide_android_version, Build.VERSION.RELEASE),
            getString(R.string.guide_screen_resolution_value, point.x, point.y),
            resources.displayMetrics.densityDpi.toString(),
            getString(
                R.string.guide_storage_value,
                NetworkSpeedSampler.format(storage.usedBytes),
                NetworkSpeedSampler.format(storage.totalBytes),
            ),
            getString(R.string.guide_complete),
        )
    }

    companion object {
        private const val PREFS_NAME = "first_run"
        private const val KEY_DEVICE_SCAN_COMPLETE = "device_scan_complete"
        private const val STEP_DELAY_MS = 650L

        fun shouldShow(context: Context): Boolean = !context
            .getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_DEVICE_SCAN_COMPLETE, false)

        private fun markComplete(context: Context) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DEVICE_SCAN_COMPLETE, true)
                .apply()
        }
    }
}
