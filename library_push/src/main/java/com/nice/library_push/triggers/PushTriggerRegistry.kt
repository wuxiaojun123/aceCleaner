package com.nice.library_push.triggers

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nice.library_push.api.PushEvent
import com.nice.library_push.business.file.FileSceneObserver
import com.nice.library_push.config.PushConfig
import com.nice.library_push.config.PushScene
import java.util.concurrent.TimeUnit

/** Owns platform listeners; one registry belongs to one initialized SDK instance. */
internal class PushTriggerRegistry(
    private val app: Application,
    private val emit: (PushEvent) -> Unit,
) : Application.ActivityLifecycleCallbacks {
    @Volatile var isForeground = false
        private set
    private var startedActivities = 0
    @Volatile private var config = PushConfig()
    private val handler = Handler(Looper.getMainLooper())
    private val receivers = mutableListOf<BroadcastReceiver>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val fileObserver = FileSceneObserver(app, emit)
    private var batteryLevel: Int? = null
    private var charging: Boolean? = null
    private var lastUnlock = 0L
    private var lastWifi = 0L
    private val backgroundRunnable = Runnable { emit(PushEvent(PushScene.BACKGROUND)) }

    init { app.registerActivityLifecycleCallbacks(this) }

    @Synchronized fun update(next: PushConfig) {
        config = next
        receivers.forEach { runCatching { app.unregisterReceiver(it) } }
        receivers.clear()
        networkCallback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        networkCallback = null
        if (next.scene(PushScene.UNLOCK).enabled) {
            val unlock = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = receive(intent)
            }
            ContextCompat.registerReceiver(app, unlock, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED)
            receivers += unlock
        }
        if (next.scene(PushScene.PACKAGE).enabled) {
            val packages = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = receive(intent)
            }
                val packageFilter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                }
            ContextCompat.registerReceiver(app, packages, packageFilter, ContextCompat.RECEIVER_EXPORTED)
            receivers += packages
        }
        batteryReceiver?.let { runCatching { app.unregisterReceiver(it) } }
        batteryReceiver = null
        if (next.scene(PushScene.BATTERY).enabled) {
            val batteryFilter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = onBattery(intent)
            }.also { ContextCompat.registerReceiver(app, it, batteryFilter, ContextCompat.RECEIVER_EXPORTED) }
        }
        if (next.scene(PushScene.WIFI).enabled) registerWifi()
        fileObserver.update(next.scene(PushScene.SCREENSHOT).enabled, next.scene(PushScene.FILE))
        scheduleTimer()
    }

    private var batteryReceiver: BroadcastReceiver? = null

    private fun receive(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_USER_PRESENT -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastUnlock >= 2_000) { lastUnlock = now; emit(PushEvent(PushScene.UNLOCK)) }
            }
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED ->
                emit(PushEvent(PushScene.PACKAGE, 1, metadata = mapOf("package" to (intent.data?.schemeSpecificPart ?: ""))))
            Intent.ACTION_PACKAGE_REMOVED -> if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
                emit(PushEvent(PushScene.PACKAGE, 2, metadata = mapOf("package" to (intent.data?.schemeSpecificPart ?: ""))))
        }
    }

    private fun registerWifi() {
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = wifi(1)
            override fun onLost(network: Network) = wifi(2)
        }
        runCatching { connectivity.registerNetworkCallback(request, callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun wifi(type: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWifi >= 2_000) { lastWifi = now; emit(PushEvent(PushScene.WIFI, type)) }
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra("level", -1)
        val scale = intent.getIntExtra("scale", -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
        val state = intent.getIntExtra("status", -1)
        val isCharging = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> true
            Intent.ACTION_POWER_DISCONNECTED -> false
            else -> state == 2 || state == 5
        }
        val oldCharging = charging
        val oldLevel = batteryLevel
        charging = isCharging
        if (percent != null) batteryLevel = percent
        if (oldCharging != null && oldCharging != isCharging) {
            emit(PushEvent(PushScene.BATTERY, if (isCharging) 1 else 2))
        } else if (oldLevel != null && percent != null && percent < oldLevel) {
            val ordered = config.scene(PushScene.BATTERY).batteryLevels.sortedDescending()
            val crossed = ordered.withIndex().filter { oldLevel > it.value && percent <= it.value }
            crossed.minByOrNull { it.value }?.let {
                emit(PushEvent(PushScene.BATTERY, 3 + it.index, metadata = mapOf("level" to percent.toString())))
            }
        }
    }

    private fun scheduleTimer() {
        val manager = WorkManager.getInstance(app)
        val scene = config.scene(PushScene.TIMER)
        if (!scene.enabled) { manager.cancelUniqueWork(PushTimerWorker.WORK_NAME); return }
        val interval = scene.effectiveCooldownSeconds().coerceAtLeast(15 * 60)
        val request = PeriodicWorkRequestBuilder<PushTimerWorker>(interval, TimeUnit.SECONDS).build()
        manager.enqueueUniquePeriodicWork(PushTimerWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun stop() {
        receivers.forEach { runCatching { app.unregisterReceiver(it) } }
        receivers.clear()
        batteryReceiver?.let { runCatching { app.unregisterReceiver(it) } }
        networkCallback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        fileObserver.stop()
        handler.removeCallbacks(backgroundRunnable)
        app.unregisterActivityLifecycleCallbacks(this)
        WorkManager.getInstance(app).cancelUniqueWork(PushTimerWorker.WORK_NAME)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        isForeground = true
        handler.removeCallbacks(backgroundRunnable)
    }
    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) {
            isForeground = false
            if (config.scene(PushScene.BACKGROUND).enabled) handler.postDelayed(backgroundRunnable, 1_000)
        }
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
