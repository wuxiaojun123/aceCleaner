package com.nice.aceclean.ui.main

import android.content.Intent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.ActivityAllowedAppsBinding
import com.nice.aceclean.notification.AllowedApp
import com.nice.aceclean.notification.AllowedAppsAdapter
import com.nice.aceclean.notification.NotificationInterceptStore
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.EventUtils
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AllowedAppsActivity : BaseActivity<ActivityAllowedAppsBinding>({ ActivityAllowedAppsBinding.inflate(it) }, R.layout.activity_allowed_apps) {
    private var allApps: List<AllowedApp> = emptyList()
    private lateinit var searchInput: EditText
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var emptyView: TextView
    private val adapter = AllowedAppsAdapter(
        isAllowed = { NotificationInterceptStore.isWhitelisted(this, it) },
        onAllowedChanged = { packageName, allowed ->
            NotificationInterceptStore.setWhitelisted(this, packageName, allowed)
            updateSelectedCount()
        },
    )

    override fun initViews() {
        binding.allowedAppsBack.setTrackedClickListener(EventUtils.CLICK_ALLOWED_APPS_BACK) { finish() }
        searchInput = binding.allowedAppsSearch
        appsRecyclerView = binding.allowedAppsList
        loadingIndicator = binding.allowedAppsLoading
        emptyView = binding.allowedAppsEmpty
        appsRecyclerView.layoutManager = LinearLayoutManager(this)
        appsRecyclerView.adapter = adapter
        searchInput.setTrackedClickListener(EventUtils.CLICK_ALLOWED_APPS_SEARCH) { showSearchKeyboard() }
        searchInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showSearchKeyboard() }
        searchInput.doAfterTextChanged { filterApps(it?.toString().orEmpty()) }
        updateSelectedCount()
        loadInstalledApps()
    }

    private fun showSearchKeyboard() {
        searchInput.requestFocus()
        searchInput.post {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun loadInstalledApps() {
        loadingIndicator.isVisible = true
        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) { loadLaunchableApps() }
            loadingIndicator.isVisible = false
            filterApps(searchInput.text?.toString().orEmpty())
        }
    }

    @Suppress("DEPRECATION")
    private fun loadLaunchableApps(): List<AllowedApp> {
        val collator = Collator.getInstance(Locale.getDefault())
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launchIntent, 0).asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.enabled && it.packageName != packageName }
            .map { AllowedApp(it.packageName, packageManager.getApplicationLabel(it).toString()) }
            .distinctBy(AllowedApp::packageName)
            .sortedWith { first, second -> collator.compare(first.appName, second.appName) }
            .toList()
    }

    private fun filterApps(query: String) {
        val normalized = query.trim().lowercase(Locale.getDefault())
        val filtered = if (normalized.isEmpty()) allApps else allApps.filter {
            it.appName.lowercase(Locale.getDefault()).contains(normalized) ||
                it.packageName.lowercase(Locale.US).contains(normalized)
        }
        adapter.submitList(filtered)
        appsRecyclerView.isVisible = filtered.isNotEmpty()
        emptyView.isVisible = filtered.isEmpty() && !loadingIndicator.isVisible
    }

    private fun updateSelectedCount() {
        binding.allowedAppsSelectedCount.text = getString(
            R.string.notif_allowed_apps_selected,
            NotificationInterceptStore.whitelist(this).size,
        )
    }
}
