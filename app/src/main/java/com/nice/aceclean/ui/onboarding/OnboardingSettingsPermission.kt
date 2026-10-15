package com.nice.aceclean.ui.onboarding

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.ad.HotStartManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Settings result/polling lifecycle; permission purpose and next page belong to the caller. */
internal class OnboardingSettingsPermission(
    private val activity: FragmentActivity,
    private val isGranted: () -> Boolean,
    private val onFinished: (Boolean) -> Unit,
) : AutoCloseable {
    private var pending = false
    private var poll: Job? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        complete()
    }

    fun restorePendingRequest() {
        pending = activity.intent.getBooleanExtra(EXTRA_PENDING, false)
        if (pending) startPolling()
    }

    fun request(vararg intents: Intent): Boolean {
        if (isGranted()) { onFinished(true); return true }
        pending = true
        activity.intent.putExtra(EXTRA_PENDING, true)
        HotStartManager.markSkipNextHotStart()
        val opened = intents.any { runCatching { launcher.launch(it) }.isSuccess }
        if (opened) startPolling() else complete()
        return opened
    }

    private fun startPolling() {
        poll?.cancel()
        poll = activity.lifecycleScope.launch {
            repeat(450) {
                delay(400L)
                if (pending && isGranted()) {
                    HotStartManager.markSkipNextHotStart()
                    activity.startActivity(Intent(activity, activity.javaClass).addFlags(
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ))
                    complete()
                    return@launch
                }
            }
        }
    }

    private fun complete() {
        if (!pending) return
        pending = false
        activity.intent.removeExtra(EXTRA_PENDING)
        poll?.cancel()
        poll = null
        onFinished(isGranted())
    }

    override fun close() { poll?.cancel(); poll = null }

    private companion object { const val EXTRA_PENDING = "onboarding_settings_pending" }
}
