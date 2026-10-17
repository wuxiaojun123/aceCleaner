package com.nice.aceclean.ui.main

import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.FragmentNotificationCleanerBinding
import com.nice.aceclean.databinding.ItemNotificationTopAppBinding
import com.nice.aceclean.notification.AppIconLoader
import com.nice.aceclean.notification.InterceptedNotificationAdapter
import com.nice.aceclean.notification.InterceptedNotificationStore
import com.nice.aceclean.notification.NotificationAppStats
import com.nice.aceclean.notification.NotificationInterceptStore
import com.nice.aceclean.notification.NotificationStatsStore
import com.nice.aceclean.util.EventUtils
import java.text.NumberFormat
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class NotificationCleanerActivity : PermissionFeatureActivity<FragmentNotificationCleanerBinding>(
    FeatureType.NOTIFICATION_CLEANER,
    { FragmentNotificationCleanerBinding.inflate(it) },
    R.layout.fragment_notification_cleaner,
) {
    private val numberFormatter = NumberFormat.getIntegerInstance()
    private val adapter = InterceptedNotificationAdapter()
    private val topAppsAdapter = TopAppsAdapter()
    private var isClearing = false

    override fun initFeatureViews() {
        setupPinnedTopBar()
        featureBinding.notificationTopApps.apply {
            layoutManager = LinearLayoutManager(this@NotificationCleanerActivity)
            adapter = topAppsAdapter
        }
        featureBinding.interceptedNotificationsList.apply {
            layoutManager = LinearLayoutManager(this@NotificationCleanerActivity)
            adapter = this@NotificationCleanerActivity.adapter
        }
        featureBinding.notificationBack.setTrackedClickListener(EventUtils.CLICK_NOTIFICATIONS_BACK) { finish() }
        featureBinding.notificationPinnedBack.setTrackedClickListener(EventUtils.CLICK_NOTIFICATIONS_PINNED_BACK) { finish() }
        featureBinding.notificationWhitelistEntry.setTrackedClickListener(EventUtils.CLICK_NOTIFICATIONS_WHITELIST) {
            startActivity(Intent(this, AllowedAppsActivity::class.java))
        }
        featureBinding.notificationInterceptSwitch.setTrackedClickListener(EventUtils.CLICK_NOTIFICATIONS_SWITCH)
        featureBinding.notificationInterceptSwitch.apply {
            setTrackOnColor(ContextCompat.getColor(this@NotificationCleanerActivity, R.color.notification_manager_amber))
            setOnCheckedChangeListener { _, checked ->
                NotificationInterceptStore.setEnabled(this@NotificationCleanerActivity, checked)
                renderStats()
            }
        }
        featureBinding.notificationCleanAll.setTrackedClickListener(EventUtils.CLICK_NOTIFICATIONS_CLEAR) { clearInterceptedNotifications() }
        observeInterceptedNotifications()
        lifecycleScope.launch(Dispatchers.IO) {
            InterceptedNotificationStore.discardExpired(this@NotificationCleanerActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFeatureShown) renderStats()
    }

    private fun renderStats() {
        val intercepting = NotificationInterceptStore.isEnabled(this)
        featureBinding.notificationInterceptSwitch.apply {
            if (isChecked != intercepting) {
                setOnCheckedChangeListener(null)
                isChecked = intercepting
                setOnCheckedChangeListener { _, checked ->
                    NotificationInterceptStore.setEnabled(this@NotificationCleanerActivity, checked)
                    renderStats()
                }
            }
        }
        featureBinding.notificationInterceptedToday.text =
            numberFormatter.format(NotificationInterceptStore.interceptedToday(this))
        featureBinding.notificationWhitelistCount.text =
            NotificationInterceptStore.whitelist(this).size.toString()

        val snapshot = NotificationStatsStore.load(this)
        featureBinding.notificationBadgeValue.text =
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
                    featureBinding.interceptedNotificationsList.isVisible = hasItems
                    featureBinding.interceptedNotificationsEmpty.isVisible = !hasItems
                    featureBinding.notificationCleanAll.apply {
                        isEnabled = hasItems
                        alpha = if (hasItems) 1f else 0.45f
                    }
                    featureBinding.notificationInterceptedToday.text =
                        numberFormatter.format(NotificationInterceptStore.interceptedToday(this@NotificationCleanerActivity))
                }
            }
        }
    }

    private fun clearInterceptedNotifications() {
        if (isClearing || adapter.itemCount == 0) return
        val clearedCount = adapter.itemCount
        isClearing = true
        featureBinding.notificationCleanAll.isEnabled = false
        featureBinding.interceptedNotificationsEmpty.isVisible = false
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
        val recyclerView = featureBinding.interceptedNotificationsList
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

    private fun setupPinnedTopBar() {
        featureBinding.notificationScrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                updatePinnedTopBar(scrollY)
            },
        )
        featureBinding.notificationScrollView.doOnLayout {
            updatePinnedTopBar(featureBinding.notificationScrollView.scrollY)
        }
    }

    private fun updatePinnedTopBar(scrollY: Int) {
        val showDistance = (featureBinding.notificationHeader.bottom +
            featureBinding.notificationScrollView.paddingTop).coerceAtLeast(1)
        val alpha = (scrollY.toFloat() / showDistance).coerceIn(0f, 1f)
        featureBinding.notificationPinnedTopBar.alpha = alpha
        featureBinding.notificationPinnedTopBar.isVisible = alpha > 0f
        featureBinding.notificationHeader.importantForAccessibility = if (alpha >= 1f) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    }

    private fun renderAppList(apps: List<NotificationAppStats>) {
        val visibleApps = apps.filter { it.packageName != packageName }
        topAppsAdapter.submitList(visibleApps)
        featureBinding.notificationTopApps.isVisible = visibleApps.isNotEmpty()
        featureBinding.notificationTopAppsEmpty.isVisible = visibleApps.isEmpty()
    }

    private inner class TopAppsAdapter : RecyclerView.Adapter<TopAppsAdapter.Holder>() {
        private var apps = emptyList<NotificationAppStats>()

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun submitList(items: List<NotificationAppStats>) {
            apps = items
            notifyDataSetChanged()
        }

        override fun getItemCount() = apps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemNotificationTopAppBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(apps[position])

        inner class Holder(private val binding: ItemNotificationTopAppBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(app: NotificationAppStats) {
                binding.appIcon.setImageDrawable(
                    AppIconLoader.load(this@NotificationCleanerActivity, app.packageName)
                        ?: ContextCompat.getDrawable(this@NotificationCleanerActivity, R.drawable.icon_notification),
                )
                binding.appName.text = app.appName
                binding.appCount.text = getString(R.string.notif_count_fmt, app.count)
                binding.root.setTrackedClickListener(EventUtils.CLICK_NOTIFICATIONS_APP) {
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName),
                    )
                }
            }
        }
    }
}
