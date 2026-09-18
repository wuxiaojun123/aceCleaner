package com.nice.aceclean.permission

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

enum class SpecialPermission {
    USAGE_ACCESS,
    NOTIFICATION_LISTENER,
}

object SpecialPermissionAccess {
    fun isGranted(context: Context, permission: SpecialPermission): Boolean = when (permission) {
        SpecialPermission.USAGE_ACCESS -> {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                context.applicationInfo.uid,
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }
        SpecialPermission.NOTIFICATION_LISTENER ->
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun settingsIntent(context: Context, permission: SpecialPermission): Intent = when (permission) {
        SpecialPermission.USAGE_ACCESS -> usageAccessSettingsIntents(context).first()
        SpecialPermission.NOTIFICATION_LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    /**
     * AOSP exposes a package-specific usage-access activity, although the public action's
     * contract does not require OEMs to honor a package URI. Prefer the resolved details
     * component, then fall back to enhanced implicit and generic list intents.
     */
    fun usageAccessSettingsIntents(context: Context): Array<Intent> {
        val packageName = context.packageName
        val packageIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            putExtra(SETTINGS_FRAGMENT_ARGUMENT_KEY, packageName)
        }
        val detailsComponent = context.packageManager
            .queryIntentActivities(packageIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .firstOrNull { component ->
                component.className.contains("AppUsageAccess", ignoreCase = true) ||
                    component.className.contains("UsageAccessDetails", ignoreCase = true)
            }
        return buildList {
            if (detailsComponent != null) add(Intent(packageIntent).setComponent(detailsComponent))
            add(packageIntent)
            add(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                putExtra(SETTINGS_FRAGMENT_ARGUMENT_KEY, packageName)
            })
        }.toTypedArray()
    }

    private const val SETTINGS_FRAGMENT_ARGUMENT_KEY = ":settings:fragment_args_key"
}
