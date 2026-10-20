package com.nice.aceclean.ui.main

import android.app.Activity
import com.nice.aceclean.R
import com.nice.aceclean.databinding.FragmentPermissionGateBinding
import com.nice.aceclean.permission.AllFilesAccessPermissionHelper
import com.nice.aceclean.permission.NotificationListenerPermissionHelper
import com.nice.aceclean.permission.MediaReadPermissionHelper
import com.nice.aceclean.permission.MediaReadType
import com.nice.aceclean.permission.UsageAccessPermissionHelper
import com.nice.aceclean.ui.base.BaseActivity

/** Permission UI is separate from the scan so denial never reaches the ad transition. */
class FeatureScanPermissionActivity : BaseActivity<FragmentPermissionGateBinding>(
    { FragmentPermissionGateBinding.inflate(it) }, R.layout.fragment_permission_gate,
) {
    private val feature get() = ScanFeature.from(intent)
    private val storage = AllFilesAccessPermissionHelper(this, ::finishGranted, {})
    private val photos =
        MediaReadPermissionHelper(this, MediaReadType.IMAGES, ::finishGranted, ::showPhotoSettings)
    private val videos =
        MediaReadPermissionHelper(this, MediaReadType.VIDEO, ::finishGranted, ::showVideoSettings)
    private val notifications = NotificationListenerPermissionHelper(this, ::finishGranted)
    private val usage = UsageAccessPermissionHelper(this, ::finishGranted)

    override fun initViews() {
        val target = feature ?: return finish()
        binding.permissionToolbarTitle.setText(target.title)
        val isTraffic = target == ScanFeature.NETWORK_TRAFFIC
        val isUsage = target.permission == ScanPermission.USAGE
        binding.permissionImage.setImageResource(
            when (target) {
                ScanFeature.NETWORK_TRAFFIC -> R.drawable.image_network_traffic_permission
                ScanFeature.APP_MANAGER -> R.drawable.image_app_manager_permission
                ScanFeature.NOTIFICATIONS -> R.drawable.notification_grant
                else -> if (isUsage) R.drawable.image_app_manager_permission else R.drawable.onboarding_image_permission_storage
            }
        )
        binding.permissionTitle.setText(
            if (isUsage) {
                if (isTraffic) R.string.network_traffic_permission_title else if (target == ScanFeature.RAM) R.string.string_ram_allow_usage_access else R.string.app_manager_permission_title
            } else if (target == ScanFeature.NOTIFICATIONS) R.string.notification_permission_title
            else R.string.storage_permission_required
        )
        binding.permissionDescription.setText(
            if (isUsage) {
                if (isTraffic) R.string.network_traffic_permission_text else if (target == ScanFeature.RAM) R.string.string_ram_usage_access_description else R.string.app_manager_permission_text
            } else if (target == ScanFeature.NOTIFICATIONS) R.string.notification_permission_text
            else R.string.storage_permission_explanation
        )
        binding.permissionBack.setOnClickListener { finish() }
        binding.permissionAllow.root.setOnClickListener {
            when (target.permission) {
                ScanPermission.STORAGE -> storage.request()
                ScanPermission.IMAGES -> photos.request()
                ScanPermission.VIDEO -> videos.request()
                ScanPermission.USAGE -> usage.request()
                ScanPermission.NOTIFICATIONS -> notifications.request()
                ScanPermission.NONE -> finishGranted()
            }
        }
    }

    private fun showPhotoSettings() {
        binding.permissionAllow.root.setText(R.string.open_app_settings)
        binding.permissionAllow.root.setOnClickListener { photos.openAppSettings() }
    }

    private fun showVideoSettings() {
        binding.permissionAllow.root.setText(R.string.open_app_settings)
        binding.permissionAllow.root.setOnClickListener { videos.openAppSettings() }
    }

    override fun onResume() {
        super.onResume()
        if (feature?.hasPermission(this) == true) {
            finishGranted()
            return
        }
        binding.permissionAllow.root.setAnimationEnabled(!isFinishing)
    }

    override fun onPause() {
        binding.permissionAllow.root.setAnimationEnabled(false)
        super.onPause()
    }

    private fun finishGranted() {
        if (feature?.hasPermission(this) != true || isFinishing) return
        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        storage.close()
        photos.close()
        videos.close()
        notifications.close()
        usage.close()
        super.onDestroy()
    }
}
