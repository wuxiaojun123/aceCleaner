package com.nice.aceclean.ui.main

/** Only known notification routes may be resumed after the startup ad. */
internal enum class NotificationDestination(val feature: ScanFeature?) {
    HOME(null),
    SCREENSHOT(ScanFeature.SCREENSHOT),
    CLEAN_UP(ScanFeature.CLEAN_UP),
    RAM(ScanFeature.RAM),
    BATTERY(ScanFeature.BATTERY),
    NETWORK(ScanFeature.NETWORK_TRAFFIC);

    companion object {
        fun from(value: String?): NotificationDestination? = entries.firstOrNull { it.name == value }
    }
}
