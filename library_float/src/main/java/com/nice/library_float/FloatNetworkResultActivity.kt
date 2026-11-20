package com.nice.library_float

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nice.library_float.databinding.ViewFloatNetSpeedBinding

class FloatNetworkResultActivity : AppCompatActivity() {
    private var closeReported = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val binding = ViewFloatNetSpeedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (FloatingBall.isEnabled()) FloatingBall.host.reportEvent("floatball_network_result_show", emptyMap())
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.clSpeedResult.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.clSpeedAnim.visibility = View.GONE
        binding.clSpeedResult.visibility = View.VISIBLE
        binding.tvNetResult.text = intent.getStringExtra("result")
        binding.tvNetUp.text = getString(R.string.string_float_result_speed, intent.getDoubleExtra("up", 0.0))
        binding.tvNetDown.text = getString(R.string.string_float_result_speed, intent.getDoubleExtra("down", 0.0))
        binding.ivClose.setOnClickListener { closeResult() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeResult()
        })
        if (FloatingBall.isEnabled()) FloatingBall.host.bindNetworkResultAd(this, binding.flAdView)
    }

    private fun closeResult() {
        if (!closeReported && FloatingBall.isEnabled()) {
            closeReported = true
            FloatingBall.host.reportEvent("floatball_network_result_close", emptyMap())
        }
        finishAndRemoveTask()
    }

    companion object {
        fun start(context: Context, resultText: String, uploadMbps: Double, downloadMbps: Double) {
            context.startActivity(Intent(context, FloatNetworkResultActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("result", resultText)
                putExtra("up", uploadMbps)
                putExtra("down", downloadMbps)
            })
        }
    }
}
