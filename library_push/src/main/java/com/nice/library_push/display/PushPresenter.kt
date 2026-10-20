package com.nice.library_push.display

import android.app.Application
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nice.library_push.R
import com.nice.library_push.api.*
import com.nice.library_push.config.PushDisplayMode
import com.nice.library_push.config.PushTemplateSpec
import com.nice.library_push.databinding.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PushPresenter(private val app: Application) {
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val windows = app.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var observerRegistered = false
    private var stopped = false
    private val dismiss = Runnable { dismissOverlay() }
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { dismissOverlay() }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { dismissOverlay() }
    }

    fun canDisplay(mode: PushDisplayMode): Boolean = when (mode) {
        PushDisplayMode.NOTIFICATION -> PushPermissions.canPostNotifications(app)
        PushDisplayMode.OVERLAY_ONLY -> PushPermissions.canShowOverlay(app)
        PushDisplayMode.OVERLAY_PREFERRED -> PushPermissions.canShowOverlay(app) || PushPermissions.canPostNotifications(app)
    }

    suspend fun show(
        id: Int, content: PushContent, spec: PushTemplateSpec, mode: PushDisplayMode,
        channelName: String, click: PushClick, isStillEligible: () -> Boolean = { true },
    ): Boolean {
        val bitmap = PushImageLoader.load(content.imageUrl)
        return withContext(Dispatchers.Main) {
            if (stopped || !isStillEligible()) return@withContext false
            when (mode) {
                PushDisplayMode.NOTIFICATION -> systemNotification(id, content, spec, channelName, click, bitmap)
                PushDisplayMode.OVERLAY_ONLY -> showOverlay(content, spec, click, id, bitmap)
                PushDisplayMode.OVERLAY_PREFERRED -> {
                    if (PushPermissions.canShowOverlay(app)) showOverlay(content, spec, click, id, bitmap)
                    else systemNotification(id, content, spec, channelName, click, bitmap)
                }
            }
        }
    }

    private fun systemNotification(id: Int, content: PushContent, spec: PushTemplateSpec, channelName: String, click: PushClick, bitmap: Bitmap?): Boolean {
        if (!PushPermissions.canPostNotifications(app)) return false
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,
                channelName.ifBlank { app.getString(R.string.nice_push_updates) }, NotificationManager.IMPORTANCE_HIGH))
            if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        val small = remoteViews(layout(content.style, Size.SMALL), content, click, id, bitmap, spec.showClose)
        val normal = remoteViews(layout(content.style, Size.NORMAL), content, click, id, bitmap, spec.showClose)
        val big = remoteViews(layout(content.style, Size.LARGE), content, click, id, bitmap, spec.showClose)
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.nice_push_icon)
            .setContentTitle(content.title).setContentText(content.body)
            .setContentIntent(pendingIntent(click, id, "content"))
            .setCustomContentView(small).setCustomBigContentView(big)
            .setCustomHeadsUpContentView(if (Build.VERSION.SDK_INT >= 33) normal else small)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setAutoCancel(spec.autoCancel).setOngoing(spec.ongoing)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        return runCatching { manager.notify(id, notification); true }.getOrDefault(false)
    }

    internal fun remoteViews(layout: Int, content: PushContent, click: PushClick, id: Int, bitmap: Bitmap?, showClose: Boolean): RemoteViews =
        RemoteViews(app.packageName, layout).apply {
            setTextViewText(R.id.nice_push_title, content.title)
            setTextViewText(R.id.nice_push_body, content.body)
            setTextViewText(R.id.nice_push_button, content.button)
            if (content.style == PushStyle.DEFAULT) {
                setViewVisibility(R.id.nice_push_button, if (content.button.isBlank()) View.GONE else View.VISIBLE)
                if (layout != R.layout.nice_push_small) {
                    setViewVisibility(R.id.nice_push_image, if (bitmap == null) View.GONE else View.VISIBLE)
                    if (bitmap != null) setImageViewBitmap(R.id.nice_push_image, bitmap)
                }
            } else {
                setTextViewText(R.id.nice_push_date, content.date)
                when (content.style) {
                    PushStyle.CALENDAR -> {
                        setTextViewText(R.id.nice_push_day, content.day)
                        setTextViewText(R.id.nice_push_weekday, content.weekday)
                    }
                    PushStyle.WEATHER -> {
                        setTextViewText(R.id.nice_push_label, content.label)
                        setImageViewResource(R.id.nice_push_image, weatherIcon(content.weatherCondition))
                        setInt(R.id.nice_push_root, "setBackgroundResource", weatherBackground(content.weatherCondition))
                    }
                    PushStyle.NEWS -> {
                        setTextViewText(R.id.nice_push_label, content.label)
                        if (bitmap != null) setImageViewBitmap(R.id.nice_push_image, bitmap)
                        else setImageViewResource(R.id.nice_push_image, R.drawable.nice_push_news_placeholder)
                    }
                    else -> Unit
                }
            }
            setViewVisibility(R.id.nice_push_close, if (showClose) View.VISIBLE else View.GONE)
            setOnClickPendingIntent(R.id.nice_push_root, pendingIntent(click, id, "content"))
            setOnClickPendingIntent(R.id.nice_push_button, pendingIntent(click, id, "button"))
            setOnClickPendingIntent(R.id.nice_push_close, pendingIntent(click, id, "close"))
        }

    private fun pendingIntent(click: PushClick, id: Int, action: String): PendingIntent {
        val intent = Intent(app, PushClickActivity::class.java).apply {
            putExtra(PushClickActivity.EXTRA_SCENE, click.scene)
            putExtra(PushClickActivity.EXTRA_TEMP_ID, click.tempId)
            putExtra(PushClickActivity.EXTRA_CONTENT_ID, click.contentId)
            putExtra(PushClickActivity.EXTRA_ACTION, action)
            putExtra(PushClickActivity.EXTRA_TARGET, click.target)
            putExtra(PushClickActivity.EXTRA_NOTIFICATION_ID, id)
            putExtra(PushClickActivity.EXTRA_TYPE, click.type)
            putExtra(PushClickActivity.EXTRA_AUTO_CANCEL, click.autoCancel)
        }
        return PendingIntent.getActivity(app, id * 3 + when (action) { "button" -> 1; "close" -> 2; else -> 0 },
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showOverlay(content: PushContent, spec: PushTemplateSpec, click: PushClick, id: Int, bitmap: Bitmap?): Boolean {
        if (!PushPermissions.canShowOverlay(app) || app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        dismissOverlay()
        val fields = overlayFields(content.style)
        fields.title.text = content.title
        fields.body.text = content.body
        fields.button.apply {
            text = content.button
            visibility = if (content.button.isBlank()) View.GONE else View.VISIBLE
            setOnClickListener { runCatching { pendingIntent(click, id, "button").send() } }
        }
        fields.close.apply {
            visibility = if (spec.showOverlayClose) View.VISIBLE else View.GONE
            setOnClickListener { runCatching { pendingIntent(click, id, "close").send() } }
        }
        fields.root.setOnClickListener { runCatching { pendingIntent(click, id, "content").send() } }
        fields.date?.text = content.date
        fields.label?.text = content.label
        fields.day?.text = content.day
        fields.weekday?.text = content.weekday
        fields.image?.let { image ->
            when (content.style) {
                PushStyle.WEATHER -> image.setImageResource(weatherIcon(content.weatherCondition))
                PushStyle.NEWS -> if (bitmap != null) image.setImageBitmap(bitmap) else image.setImageResource(R.drawable.nice_push_news_placeholder)
                else -> {
                    image.visibility = if (bitmap != null) View.VISIBLE else View.GONE
                    if (bitmap != null) image.setImageBitmap(bitmap)
                }
            }
        }
        if (content.style == PushStyle.WEATHER) fields.root.setBackgroundResource(weatherBackground(content.weatherCondition))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        return runCatching {
            windows.addView(fields.root, params)
            overlay = fields.root
            ensureDismissObservers()
            fields.root.alpha = 0f
            fields.root.animate().alpha(1f).setDuration(280).start()
            handler.postDelayed(dismiss, 8_000)
            true
        }.onFailure { dismissOverlay() }.getOrDefault(false)
    }

    private fun ensureDismissObservers() {
        if (observerRegistered) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        ContextCompat.registerReceiver(app, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        observerRegistered = true
    }

    private data class OverlayFields(
        val root: View, val title: TextView, val body: TextView, val button: TextView, val close: View,
        val image: ImageView? = null, val label: TextView? = null, val date: TextView? = null,
        val day: TextView? = null, val weekday: TextView? = null,
    )

    private fun overlayFields(style: PushStyle): OverlayFields {
        val inflater = LayoutInflater.from(app)
        return when (style) {
            PushStyle.CALENDAR -> NicePushCalendarOverlayBinding.inflate(inflater).let {
                OverlayFields(it.root, it.nicePushTitle, it.nicePushBody, it.nicePushButton, it.nicePushClose,
                    date = it.nicePushDate, day = it.nicePushDay, weekday = it.nicePushWeekday)
            }
            PushStyle.WEATHER -> NicePushWeatherOverlayBinding.inflate(inflater).let {
                OverlayFields(it.root, it.nicePushTitle, it.nicePushBody, it.nicePushButton, it.nicePushClose,
                    it.nicePushImage, it.nicePushLabel, it.nicePushDate)
            }
            PushStyle.NEWS -> NicePushNewsOverlayBinding.inflate(inflater).let {
                OverlayFields(it.root, it.nicePushTitle, it.nicePushBody, it.nicePushButton, it.nicePushClose,
                    it.nicePushImage, it.nicePushLabel, it.nicePushDate)
            }
            PushStyle.DEFAULT -> NicePushOverlayBinding.inflate(inflater).let {
                OverlayFields(it.root, it.nicePushTitle, it.nicePushBody, it.nicePushButton, it.nicePushClose, it.nicePushImage)
            }
        }
    }

    fun dismissOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { dismissOverlay() }; return }
        handler.removeCallbacks(dismiss)
        val view = overlay ?: return
        overlay = null
        view.animate().cancel()
        runCatching { windows.removeView(view) }
    }

    fun stop() {
        handler.post {
            stopped = true
            dismissOverlay()
            if (observerRegistered) {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
                runCatching { app.unregisterReceiver(screenReceiver) }
                observerRegistered = false
            }
        }
    }

    internal enum class Size { SMALL, NORMAL, LARGE, OVERLAY }

    internal fun layout(style: PushStyle, size: Size): Int = when (style) {
        PushStyle.CALENDAR -> when (size) {
            Size.SMALL -> R.layout.nice_push_calendar_small
            Size.NORMAL -> R.layout.nice_push_calendar_normal
            Size.LARGE -> R.layout.nice_push_calendar_large
            Size.OVERLAY -> R.layout.nice_push_calendar_overlay
        }
        PushStyle.WEATHER -> when (size) {
            Size.SMALL -> R.layout.nice_push_weather_small
            Size.NORMAL -> R.layout.nice_push_weather_normal
            Size.LARGE -> R.layout.nice_push_weather_large
            Size.OVERLAY -> R.layout.nice_push_weather_overlay
        }
        PushStyle.NEWS -> when (size) {
            Size.SMALL -> R.layout.nice_push_news_small
            Size.NORMAL -> R.layout.nice_push_news_normal
            Size.LARGE -> R.layout.nice_push_news_large
            Size.OVERLAY -> R.layout.nice_push_news_overlay
        }
        PushStyle.DEFAULT -> when (size) {
            Size.SMALL -> R.layout.nice_push_small
            Size.NORMAL, Size.LARGE -> R.layout.nice_push_large
            Size.OVERLAY -> R.layout.nice_push_overlay
        }
    }

    private fun weatherIcon(condition: WeatherCondition): Int = when (condition) {
        WeatherCondition.RAIN, WeatherCondition.STORM -> R.drawable.nice_push_weather_rain
        WeatherCondition.HOT, WeatherCondition.CLEAR -> R.drawable.nice_push_weather_sun
        WeatherCondition.CLOUDY -> R.drawable.nice_push_weather_cloud
    }

    private fun weatherBackground(condition: WeatherCondition): Int = when (condition) {
        WeatherCondition.HOT, WeatherCondition.CLEAR -> R.drawable.nice_push_weather_hot_background
        else -> R.drawable.nice_push_weather_background
    }

    companion object { const val CHANNEL_ID = "nice_push_updates" }
}
