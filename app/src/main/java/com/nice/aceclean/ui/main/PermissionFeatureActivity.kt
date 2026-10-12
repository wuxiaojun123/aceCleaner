package com.nice.aceclean.ui.main

import android.view.View
import android.view.LayoutInflater
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import com.nice.aceclean.R
import com.nice.aceclean.databinding.FragmentPermissionGateBinding
import com.nice.aceclean.permission.NotificationListenerPermissionHelper
import com.nice.aceclean.permission.SpecialPermission
import com.nice.aceclean.permission.SpecialPermissionAccess
import com.nice.aceclean.permission.UsageAccessPermissionHelper
import com.nice.aceclean.ui.base.BaseActivity

enum class FeatureType { NETWORK_TRAFFIC, NOTIFICATION_CLEANER, APP_MANAGER }

abstract class PermissionFeatureActivity<F : ViewBinding>(
    private val featureType: FeatureType,
    private val inflateFeature: (LayoutInflater) -> F,
    @param:LayoutRes private val featureLayout: Int,
) : BaseActivity<FragmentPermissionGateBinding>({ FragmentPermissionGateBinding.inflate(it) }, R.layout.fragment_permission_gate) {

    protected lateinit var featureBinding: F
        private set
    protected val isFeatureShown get() = featureShown

    private var permissionHelper: AutoCloseable? = null
    private var featureShown = false

    final override fun initViews() {
        if (SpecialPermissionAccess.isGranted(this, featureType.specialPermission)) {
            showFeature()
            return
        }
        val config = featureType.config
        binding.permissionToolbarTitle.setText(config.toolbarTitle)
        binding.permissionImage.setImageResource(config.image)
        binding.permissionTitle.setText(config.title)
        binding.permissionDescription.setText(config.description)
        binding.permissionBack.setOnClickListener { finish() }
        val requestPermission: () -> Unit = when (featureType.specialPermission) {
            SpecialPermission.NOTIFICATION_LISTENER -> {
                NotificationListenerPermissionHelper(this, ::showFeature).also { permissionHelper = it }
                    .let { helper -> { helper.request() } }
            }
            SpecialPermission.USAGE_ACCESS -> {
                UsageAccessPermissionHelper(this, ::showFeature).also { permissionHelper = it }
                    .let { helper -> { helper.request() } }
            }
        }
        binding.permissionAllow.setOnClickListener {
            requestPermission()
        }
    }

    private fun showFeature() {
        if (featureShown) return
        featureShown = true
        permissionHelper?.close()
        permissionHelper = null
        featureBinding = setActivityContent(inflateFeature(layoutInflater), featureLayout)
        initFeatureViews()
    }

    protected abstract fun initFeatureViews()

    override fun onDestroy() {
        permissionHelper?.close()
        permissionHelper = null
        super.onDestroy()
    }

    private data class GateConfig(val toolbarTitle: Int, val image: Int, val title: Int, val description: Int)

    private val FeatureType.config: GateConfig
        get() = when (this) {
            FeatureType.NETWORK_TRAFFIC -> GateConfig(
                R.string.network_traffic,
                R.drawable.image_network_traffic_permission,
                R.string.network_traffic_permission_title,
                R.string.network_traffic_permission_text,
            )
            FeatureType.NOTIFICATION_CLEANER -> GateConfig(
                R.string.notification_cleaner,
                R.drawable.notification_grant,
                R.string.notification_permission_title,
                R.string.notification_permission_text,
            )
            FeatureType.APP_MANAGER -> GateConfig(
                R.string.app_manager,
                R.drawable.image_app_manager_permission,
                R.string.app_manager_permission_title,
                R.string.app_manager_permission_text,
            )
        }

    private val FeatureType.specialPermission: SpecialPermission
        get() = when (this) {
            FeatureType.NOTIFICATION_CLEANER -> SpecialPermission.NOTIFICATION_LISTENER
            FeatureType.NETWORK_TRAFFIC, FeatureType.APP_MANAGER -> SpecialPermission.USAGE_ACCESS
        }
}
