package com.nice.aceclean.ui.main

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nice.aceclean.R
import com.nice.aceclean.permission.PermissionAutoNavigator
import com.nice.aceclean.permission.SpecialPermission
import com.nice.aceclean.permission.SpecialPermissionAccess
import com.nice.aceclean.ui.base.BaseFragment

enum class FeatureType {
    NETWORK_TRAFFIC,
    NOTIFICATION_CLEANER,
    APP_MANAGER,
}

class PermissionGateFragment : BaseFragment(R.layout.fragment_permission_gate) {

    private var permissionNavigator: PermissionAutoNavigator? = null

    private val featureType: FeatureType
        get() = FeatureType.valueOf(requireArguments().getString(ARG_FEATURE).orEmpty())

    override fun initViews(root: View) {
        val config = config()
        root.findViewById<TextView>(R.id.permission_toolbar_title).setText(config.toolbarTitle)
        root.findViewById<ImageView>(R.id.permission_image).setImageResource(config.image)
        root.findViewById<TextView>(R.id.permission_title).setText(config.title)
        root.findViewById<TextView>(R.id.permission_description).setText(config.description)
        root.findViewById<View>(R.id.permission_back).setOnClickListener {
            requireActivity().finish()
        }
        root.findViewById<View>(R.id.permission_allow).setOnClickListener { openSettings() }

        permissionNavigator = PermissionAutoNavigator(
            lifecycleOwner = viewLifecycleOwner,
            isGranted = {
                SpecialPermissionAccess.isGranted(requireContext(), featureType.specialPermission)
            },
            onGranted = ::openFeatureScreen,
        )
    }

    private fun openSettings() {
        startActivity(SpecialPermissionAccess.settingsIntent(requireContext(), featureType.specialPermission))
    }

    private fun openFeatureScreen() {
        val fragment = when (featureType) {
            FeatureType.NETWORK_TRAFFIC -> NetworkTrafficFragment()
            FeatureType.NOTIFICATION_CLEANER -> NotificationCleanerFragment()
            FeatureType.APP_MANAGER -> AppManagerFragment()
        }
        (activity as? FeatureActivity)?.showFeatureScreen(fragment)
    }

    override fun onDestroyView() {
        permissionNavigator?.close()
        permissionNavigator = null
        super.onDestroyView()
    }

    private fun config(): GateConfig = when (featureType) {
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

    private data class GateConfig(val toolbarTitle: Int, val image: Int, val title: Int, val description: Int)

    private val FeatureType.specialPermission: SpecialPermission
        get() = when (this) {
            FeatureType.NOTIFICATION_CLEANER -> SpecialPermission.NOTIFICATION_LISTENER
            FeatureType.NETWORK_TRAFFIC, FeatureType.APP_MANAGER -> SpecialPermission.USAGE_ACCESS
        }

    companion object {
        private const val ARG_FEATURE = "feature"

        fun newInstance(featureType: FeatureType) = PermissionGateFragment().apply {
            arguments = Bundle().apply { putString(ARG_FEATURE, featureType.name) }
        }
    }
}
