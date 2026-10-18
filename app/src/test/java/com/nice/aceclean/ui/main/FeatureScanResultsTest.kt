package com.nice.aceclean.ui.main

import com.nice.aceclean.cleanup.CleanupScanResult
import com.nice.aceclean.util.NetworkUsageSnapshot
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FeatureScanResultsTest {
    @Test fun resultIsTransferredOnceWithoutCopyingLargeLists() {
        val result = FeatureScanResult.Cleanup(CleanupScanResult(emptyList()))
        val token = FeatureScanResults.put(result)
        assertSame(result, FeatureScanResults.take(token))
        assertNull(FeatureScanResults.take(token))
    }

    @Test fun independentScansCannotConsumeEachOthersResults() {
        val cleanup = FeatureScanResult.Cleanup(CleanupScanResult(emptyList()))
        val traffic = FeatureScanResult.Traffic(NetworkUsageSnapshot(7, 11, mapOf(123 to 18L), listOf(18)), emptyList())
        val first = FeatureScanResults.put(cleanup)
        val second = FeatureScanResults.put(traffic)
        assertNotEquals(first, second)
        assertSame(traffic, FeatureScanResults.take(second))
        assertSame(cleanup, FeatureScanResults.take(first))
    }

    @Test fun abandonedHandoffsAreBoundedAndMissingTokensReturnNoData() {
        val result = FeatureScanResult.Apps(emptyList())
        val oldest = FeatureScanResults.put(result)
        val tokens = List(4) { FeatureScanResults.put(result) }
        assertNull(FeatureScanResults.take(oldest))
        tokens.forEach { assertSame(result, FeatureScanResults.take(it)) }
        assertNull(FeatureScanResults.take("expired-or-missing"))
        assertNull(FeatureScanResults.take(null))
    }
    @Test fun eachFeatureAcceptsOnlyItsOwnPayload() {
        val payloads = mapOf(
            ScanFeature.CLEAN_UP to FeatureScanResult.Cleanup(CleanupScanResult(emptyList())),
            ScanFeature.NETWORK_TRAFFIC to FeatureScanResult.Traffic(NetworkUsageSnapshot(0, 0, emptyMap(), emptyList()), emptyList()),
            ScanFeature.DUPLICATE_PHOTO to FeatureScanResult.Photos(emptyList()),
            ScanFeature.APP_MANAGER to FeatureScanResult.Apps(emptyList()),
            ScanFeature.SCREENSHOT to FeatureScanResult.Screenshots(emptyList(), emptyList()),
            ScanFeature.BIG_FILE to FeatureScanResult.Files(ScanFeature.BIG_FILE, emptyList()),
            ScanFeature.VIDEO to FeatureScanResult.Files(ScanFeature.VIDEO, emptyList()),
            ScanFeature.NOTIFICATIONS to FeatureScanResult.Notifications(
                com.nice.aceclean.notification.NotificationStatsSnapshot(0, emptyList()), emptyList()),
            ScanFeature.RAM to FeatureScanResult.Ram(com.nice.aceclean.util.MemorySnapshot(100, 40), emptyList()),
            ScanFeature.DEVICE to FeatureScanResult.Device(com.nice.aceclean.util.DeviceReport(
                "13", "cpu", "memory", "storage", 40, "720x1600", "ip", "3.0", null, null)),
            ScanFeature.BATTERY to FeatureScanResult.Battery(com.nice.aceclean.util.BatterySnapshot(
                70, 1, 1, "Li-ion", 30.0, 4.0, null, null, null)),
        )
        org.junit.Assert.assertEquals(ScanFeature.entries.toSet(), payloads.keys)
        payloads.forEach { (owner, result) ->
            ScanFeature.entries.forEach { target ->
                org.junit.Assert.assertEquals("$owner payload requested by $target", owner == target, target.accepts(result))
            }
        }
    }
}
