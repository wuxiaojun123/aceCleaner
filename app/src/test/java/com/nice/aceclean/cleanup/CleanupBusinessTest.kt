package com.nice.aceclean.cleanup

import com.nice.aceclean.util.NetworkUsageSnapshot
import com.nice.aceclean.util.StorageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CleanupBusinessTest {
    @Test
    fun `classifier recognizes supported cleanup candidates`() {
        assertEquals(CleanupCategory.APK_FILES, CleanupClassifier.classify("installer.APK", 1024))
        assertEquals(CleanupCategory.AD_FILES, CleanupClassifier.classify("banner.webp", 1024, "/storage/emulated/0/app/adcache/banner.webp"))
        assertEquals(CleanupCategory.RESIDUAL_FILES, CleanupClassifier.classify("settings.bak", 24))
        assertEquals(CleanupCategory.RESIDUAL_FILES, CleanupClassifier.classify("data.bin", 24, "/storage/emulated/0/.trash/data.bin"))
        assertEquals(CleanupCategory.TEMP_FILES, CleanupClassifier.classify("download.tmp", 24))
        assertEquals(CleanupCategory.TEMP_FILES, CleanupClassifier.classify("error.log", 24))
        assertEquals(CleanupCategory.TEMP_FILES, CleanupClassifier.classify("download.crdownload", 24))
        assertEquals(CleanupCategory.TEMP_FILES, CleanupClassifier.classify("photo.jpg", 24, "/storage/emulated/0/DCIM/.thumbnails/photo.jpg"))
        assertEquals(CleanupCategory.EMPTY_FILES, CleanupClassifier.classify("empty.txt", 0))
        assertNull(CleanupClassifier.classify(".nomedia", 0))
        assertNull(CleanupClassifier.classify("holiday.jpg", 2048))
    }

    @Test
    fun `scan result totals selected categories correctly`() {
        val result = CleanupScanResult(
            listOf(
                CleanupCandidate(CleanupCategory.APP_CACHE, 100),
                CleanupCandidate(CleanupCategory.APP_CACHE, 50),
                CleanupCandidate(CleanupCategory.APK_FILES, 400),
            ),
        )
        assertEquals(550L, result.totalBytes)
        assertEquals(150L, result.size(CleanupCategory.APP_CACHE))
        assertEquals(1, result.candidates(CleanupCategory.APK_FILES).size)
    }

    @Test
    fun `storage snapshot computes bounded used percentage`() {
        assertEquals(75, StorageSnapshot(1_000, 250).usedPercent)
        assertEquals(0, StorageSnapshot(0, 0).usedPercent)
        assertEquals(100, StorageSnapshot(100, -20).usedPercent)
    }

    @Test
    fun `network snapshot combines mobile and wifi totals`() {
        val snapshot = NetworkUsageSnapshot(120, 80, mapOf(1000 to 200), List(7) { 0 })
        assertEquals(200L, snapshot.totalBytes)
    }
}
