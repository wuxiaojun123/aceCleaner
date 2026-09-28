package com.nice.aceclean.ui.main

import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.notification.AppIconLoader
import com.nice.aceclean.notification.InterceptedNotificationAdapter
import com.nice.aceclean.notification.InterceptedNotificationStore
import com.nice.aceclean.notification.NotificationAppStats
import com.nice.aceclean.notification.NotificationInterceptStore
import com.nice.aceclean.notification.NotificationStatsStore
import com.nice.aceclean.ui.widget.IosSwitchView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import kotlin.coroutines.resume

class NotificationCleanerActivity : PermissionFeatureActivity(
    FeatureType.NOTIFICATION_CLEANER,
    R.layout.fragment_notification_cleaner,
) {
    private val numberFormatter = NumberFormat.getIntegerInstance()
    private val adapter = InterceptedNotificationAdapter()
    private var isClearing = false

    override fun initFeatureViews() {
        view<RecyclerView>(R.id.intercepted_notifications_list).apply {
            layoutManager = LinearLayoutManager(this@NotificationCleanerActivity)
            adapter = this@NotificationCleanerActivity.adapter
        }
        view<View>(R.id.notification_back).setOnClickListener { finish() }
        view<View>(R.id.notification_whitelist_entry).setOnClickListener {
            startActivity(Intent(this, AllowedAppsActivity::class.java))
        }
        view<IosSwitchView>(R.id.notification_intercept_switch).apply {
            setTrackOnColor(ContextCompat.getColor(this@NotificationCleanerActivity, R.color.notification_manager_amber))
            setOnCheckedChangeListener { _, checked ->
                NotificationInterceptStore.setEnabled(this@NotificationCleanerActivity, checked)
                renderStats()
            }
        }
        view<View>(R.id.notification_clean_all).setOnClickListener { clearInterceptedNotifications() }
        observeInterceptedNotifications()
        lifecycleScope.launch(Dispatchers.IO) {
            InterceptedNotificationStore.discardExpired(this@NotificationCleanerActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        if (findViewById<View?>(R.id.notification_intercept_switch) != null) renderStats()
    }

    private fun renderStats() {
        val intercepting = NotificationInterceptStore.isEnabled(this)
        view<IosSwitchView>(R.id.notification_intercept_switch).apply {
            if (isChecked != intercepting) {
                setOnCheckedChangeListener(null)
                isChecked = intercepting
                setOnCheckedChangeListener { _, checked ->
                    NotificationInterceptStore.setEnabled(this@NotificationCleanerActivity, checked)
                    renderStats()
                }
            }
        }
        view<TextView>(R.id.notification_intercepted_today).text =
            numberFormatter.format(NotificationInterceptStore.interceptedToday(this))
        view<TextView>(R.id.notification_whitelist_count).text =
            NotificationInterceptStore.whitelist(this).size.toString()

        val snapshot = NotificationStatsStore.load(this)
        view<TextView>(R.id.notification_badge_value).text =
            if (snapshot.totalToday > 99) getString(R.string.notif_badge) else snapshot.totalToday.toString()
        renderAppList(snapshot.topApps)
    }

    private fun observeInterceptedNotifications() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                InterceptedNotificationStore.observeToday(this@NotificationCleanerActivity).collect { items ->
                    if (isClearing) return@collect
                    adapter.submitList(items)
                    val hasItems = items.isNotEmpty()
                    view<RecyclerView>(R.id.intercepted_notifications_list).isVisible = hasItems
                    view<TextView>(R.id.intercepted_notifications_empty).isVisible = !hasItems
                    view<View>(R.id.notification_clean_all).apply {
                        isEnabled = hasItems
                        alpha = if (hasItems) 1f else 0.45f
                    }
                    view<TextView>(R.id.notification_intercepted_today).text =
                        numberFormatter.format(NotificationInterceptStore.interceptedToday(this@NotificationCleanerActivity))
                }
            }
        }
    }

    private fun clearInterceptedNotifications() {
        if (isClearing || adapter.itemCount == 0) return
        val clearedCount = adapter.itemCount
        isClearing = true
        view<View>(R.id.notification_clean_all).isEnabled = false
        view<TextView>(R.id.intercepted_notifications_empty).isVisible = false
        val cutoff = System.currentTimeMillis()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                NotificationInterceptStore.clearInterceptedToday(this@NotificationCleanerActivity, cutoff)
                InterceptedNotificationStore.clearTodayThrough(this@NotificationCleanerActivity, cutoff)
            }
            animateNotificationsOut()
            delay(120L)
            startActivity(NotificationCleaningSuccessActivity.createIntent(this@NotificationCleanerActivity, clearedCount))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    private suspend fun animateNotificationsOut() {
        val recyclerView = view<RecyclerView>(R.id.intercepted_notifications_list)
        val previousAnimator = recyclerView.itemAnimator
        recyclerView.itemAnimator = null
        recyclerView.scrollToPosition(0)
        suspendCancellableCoroutine { continuation ->
            recyclerView.post { if (continuation.isActive) continuation.resume(Unit) }
        }
        val visibleItems = (0 until recyclerView.childCount)
            .map(recyclerView::getChildAt)
            .sortedBy(View::getTop)
        coroutineScope {
            visibleItems.forEachIndexed { index, itemView ->
                launch {
                    delay(index * 35L)
                    suspendCancellableCoroutine { continuation ->
                        itemView.animate()
                            .translationX(recyclerView.width + itemView.width.toFloat())
                            .alpha(0f)
                            .setDuration(450L)
                            .setInterpolator(AccelerateInterpolator())
                            .withEndAction { if (continuation.isActive) continuation.resume(Unit) }
                            .start()
                        continuation.invokeOnCancellation { itemView.animate().cancel() }
                    }
                }
            }
        }
        adapter.clearDisplayedItems()
        recyclerView.itemAnimator = previousAnimator
    }

    private fun renderAppList(apps: List<NotificationAppStats>) {
        val container = view<LinearLayout>(R.id.notification_top_apps)
        container.removeAllViews()
        val visibleApps = apps.filter { it.packageName != packageName }
        if (visibleApps.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.notif_no_data)
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@NotificationCleanerActivity, R.color.notification_manager_muted))
            })
            return
        }
        visibleApps.forEachIndexed { index, app -> container.addView(createAppRow(app, index > 0)) }
    }

    private fun createAppRow(app: NotificationAppStats, hasTopMargin: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_notif_app_row)
            setPadding(dp(13), dp(11), dp(13), dp(11))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { if (hasTopMargin) topMargin = dp(10) }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName))
            }
        }
        val icon = AppIconLoader.load(this, app.packageName)
        row.addView(if (icon != null) ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(icon)
        } else TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@NotificationCleanerActivity, R.color.white))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_notif_app_shopping)
            text = app.appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        })
        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(11)
            }
            text = app.appName
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@NotificationCleanerActivity, R.color.home_text))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = getString(R.string.notif_count_fmt, app.count)
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@NotificationCleanerActivity, R.color.notification_manager_amber))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        return row
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
