package com.nice.library_float

internal enum class FloatBallState {
    DEFAULT, FILES_ACCESS_NEEDED, JUNK_CLEAN, IMAGE_CLEAN, NOTIFICATION_CLEAN, PROCESS_CLEAN, NETWORK_TEST,
}

internal object FloatReminderPolicy {
    fun resolve(
        missingPermission: Boolean,
        cooldownElapsed: Boolean,
        newDownload: Boolean,
        newImage: Boolean,
        newNotifications: Int,
        memoryUsedPercent: Int,
    ): FloatBallState = when {
        missingPermission -> FloatBallState.FILES_ACCESS_NEEDED
        !cooldownElapsed -> FloatBallState.DEFAULT
        newDownload -> FloatBallState.JUNK_CLEAN
        newImage -> FloatBallState.IMAGE_CLEAN
        newNotifications > 3 -> FloatBallState.NOTIFICATION_CLEAN
        memoryUsedPercent >= 80 -> FloatBallState.PROCESS_CLEAN
        else -> FloatBallState.NETWORK_TEST
    }
}
