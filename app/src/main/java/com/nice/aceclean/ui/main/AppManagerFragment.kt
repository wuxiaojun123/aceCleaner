package com.nice.aceclean.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.util.DeviceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppManagerFragment : BaseFragment(R.layout.fragment_app_manager) {

    private val selectedPackages = linkedSetOf<String>()
    private val uninstallQueue = ArrayDeque<String>()
    private lateinit var rootView: View

    private val uninstallLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            uninstallQueue.clear()
            Toast.makeText(requireContext(), R.string.uninstall_cancelled, Toast.LENGTH_SHORT).show()
            loadApps()
        } else if (uninstallQueue.isNotEmpty()) {
            uninstallNext()
        } else {
            loadApps()
        }
    }

    override fun initViews(root: View) {
        rootView = root
        root.findViewById<View>(R.id.app_manager_back).setOnClickListener { requireActivity().finish() }
        root.findViewById<View>(R.id.app_manager_uninstall).setOnClickListener {
            if (selectedPackages.isEmpty()) {
                Toast.makeText(requireContext(), R.string.select_apps_first, Toast.LENGTH_SHORT).show()
            } else {
                uninstallQueue.clear()
                uninstallQueue.addAll(selectedPackages)
                uninstallNext()
            }
        }
        loadApps()
    }

    private fun loadApps() {
        if (!::rootView.isInitialized) return
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { DeviceStats.launchableApps(requireContext()) }
            if (!isAdded) return@launch
            selectedPackages.clear()
            updateButton()
            rootView.findViewById<TextView>(R.id.app_manager_count).text = getString(R.string.apps_found, apps.size)
            val container = rootView.findViewById<LinearLayout>(R.id.app_manager_list)
            container.removeAllViews()
            apps.forEach { app ->
                val item = LayoutInflater.from(requireContext()).inflate(R.layout.item_app_manager, container, false)
                item.findViewById<ImageView>(R.id.app_manager_icon).setImageDrawable(requireContext().packageManager.getApplicationIcon(app.applicationInfo))
                item.findViewById<TextView>(R.id.app_manager_name).text = app.label
                item.findViewById<TextView>(R.id.app_manager_meta).text = getString(
                    R.string.last_used_value,
                    DeviceStats.formatLastUsed(app.lastUsed, getString(R.string.never_used)),
                )
                item.findViewById<TextView>(R.id.app_manager_size).text = DeviceStats.formatBytes(requireContext(), app.sizeBytes)
                val check = item.findViewById<CheckBox>(R.id.app_manager_check)
                check.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedPackages.add(app.packageName) else selectedPackages.remove(app.packageName)
                    updateButton()
                }
                item.setOnClickListener { check.toggle() }
                container.addView(item)
            }
        }
    }

    private fun updateButton() {
        if (!::rootView.isInitialized) return
        rootView.findViewById<TextView>(R.id.app_manager_uninstall).text = if (selectedPackages.isEmpty()) {
            getString(R.string.uninstall)
        } else {
            getString(R.string.uninstall_count, selectedPackages.size)
        }
    }

    private fun uninstallNext() {
        if (uninstallQueue.isEmpty()) {
            loadApps()
            return
        }
        val packageName = uninstallQueue.removeFirst()
        uninstallLauncher.launch(
            @Suppress("DEPRECATION")
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName")).apply {
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            },
        )
    }
}
