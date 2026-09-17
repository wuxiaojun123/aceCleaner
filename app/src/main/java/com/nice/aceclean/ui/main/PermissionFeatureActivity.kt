package com.nice.aceclean.ui.main

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import com.nice.aceclean.R
import com.nice.aceclean.permission.PermissionAutoNavigator
import com.nice.aceclean.permission.SpecialPermission
import com.nice.aceclean.permission.SpecialPermissionAccess
import com.nice.aceclean.ui.base.BaseActivity

enum class FeatureType { NETWORK_TRAFFIC, NOTIFICATION_CLEANER, APP_MANAGER }

abstract class PermissionFeatureActivity(
    private val featureType: FeatureType,
    @param:LayoutRes private val featureLayout: Int,
) : BaseActivity(R.layout.fragment_permission_gate) {

    private var permissionNavigator: PermissionAutoNavigator? = null
    private var featureShown = false

    final override fun initViews() {
        if (SpecialPermissionAccess.isGranted(this, featureType.specialPermission)) {
            showFeature()
            return
        }
        val config = featureType.config
        view<TextView>(R.id.permission_toolbar_title).setText(config.toolbarTitle)
        view<ImageView>(R.id.permission_image).setImageResource(config.image)
        view<TextView>(R.id.permission_title).setText(config.title)
        view<TextView>(R.id.permission_description).setText(config.description)
        view<View>(R.id.permission_back).setOnClickListener { finish() }
        view<View>(R.id.permission_allow).setOnClickListener {
            startActivity(SpecialPermissionAccess.settingsIntent(this, featureType.specialPermission))
        }
        permissionNavigator = PermissionAutoNavigator(
            lifecycleOwner = this,
            isGranted = { SpecialPermissionAccess.isGranted(this, featureType.specialPermission) },
            onGranted = ::showFeature,
        )
    }

    private fun showFeature() {
        if (featureShown) return
        featureShown = true
        permissionNavigator?.close()
        permissionNavigator = null
        setActivityContent(featureLayout)
        initFeatureViews()
    }

    protected abstract fun initFeatureViews()

    override fun onDestroy() {
        permissionNavigator?.close()
        permissionNavigator = null
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
