package com.nice.aceclean.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.core.text.HtmlCompat
import com.nice.aceclean.R
import com.nice.aceclean.ad.HotStartManager
import com.nice.aceclean.databinding.ActivityDefaultLauncherTipsBinding
import com.nice.aceclean.ui.base.BaseActivity
import java.lang.ref.WeakReference

/** A separate translucent task, dismissed to reveal the system Settings page underneath. */
class DefaultLauncherTipsActivity : BaseActivity<ActivityDefaultLauncherTipsBinding>(
    ActivityDefaultLauncherTipsBinding::inflate,
    R.layout.activity_default_launcher_tips,
) {
    override val navigationBarColorRes: Int = android.R.color.transparent

    override fun initViews() {
        current = WeakReference(this)
        HotStartManager.markSkipNextHotStart()
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        renderTips()
        binding.stopAppMessage.text = HtmlCompat.fromHtml(
            getString(R.string.string_stop_app_tips), HtmlCompat.FROM_HTML_MODE_LEGACY,
        )
        binding.root.setOnClickListener { finishAndRemoveTask() }
        binding.launcherTips.setOnClickListener { finishAndRemoveTask() }
        binding.stopAppTips.setOnClickListener { finishAndRemoveTask() }
        binding.gotIt.setOnClickListener { finishAndRemoveTask() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishAndRemoveTask()
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renderTips()
    }

    private fun renderTips() {
        val stopApp = intent.getBooleanExtra(EXTRA_STOP_APP, false)
        binding.launcherTips.visibility = if (stopApp) View.GONE else View.VISIBLE
        binding.stopAppTips.visibility = if (stopApp) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_STOP_APP = "stop_app"
        private var current: WeakReference<DefaultLauncherTipsActivity>? = null

        fun start(context: Context) = open(context, stopApp = false)

        fun startForStopApp(context: Context) = open(context, stopApp = true)

        fun dismiss() {
            current?.get()?.finishAndRemoveTask()
        }

        private fun open(context: Context, stopApp: Boolean) {
            HotStartManager.markSkipNextHotStart()
            context.startActivity(Intent(context, DefaultLauncherTipsActivity::class.java).apply {
                putExtra(EXTRA_STOP_APP, stopApp)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
