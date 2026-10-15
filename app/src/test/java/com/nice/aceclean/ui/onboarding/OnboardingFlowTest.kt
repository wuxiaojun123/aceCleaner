package com.nice.aceclean.ui.onboarding

import org.junit.Assert.*
import org.junit.Test

class OnboardingFlowTest {
    @Test fun `each onboarding page has a separate activity destination`() {
        val pages = OnboardingStep.entries.filter { it != OnboardingStep.NOT_STARTED && it != OnboardingStep.COMPLETED }
        assertEquals(10, pages.size)
        assertEquals(pages.size, pages.map(OnboardingNavigator::destination).toSet().size)
    }

    @Test fun `new users continue to device scan after splash while overlay guide is disabled`() {
        assertEquals(OnboardingStep.DEVICE_SCAN, OnboardingStep.NOT_STARTED.restored(false))
        assertEquals(OnboardingStep.DEVICE_SCAN, OnboardingStep.NOT_STARTED.restored(true))
        assertEquals(OnboardingStep.DEVICE_SCAN, OnboardingStep.OVERLAY.restored(false))
        assertEquals(OnboardingStep.COMPLETED, OnboardingStep.COMPLETED.restored(false))
    }

    @Test fun `cold start rescans device detail and never resumes stale deletion targets`() {
        assertEquals(OnboardingStep.DEVICE_SCAN, OnboardingStep.DEVICE_DETAIL.restored(false))
        assertEquals(OnboardingStep.DEVICE_DETAIL, OnboardingStep.DEVICE_DETAIL.restored(true))
        for (hot in listOf(false, true)) {
            assertEquals(OnboardingStep.JUNK_SCAN, OnboardingStep.JUNK_RESULT.restored(hot))
            assertEquals(OnboardingStep.JUNK_SCAN, OnboardingStep.CLEANING.restored(hot))
        }
    }

    @Test fun `battery branch restores independently and completed guide remains available`() {
        assertEquals(OnboardingStep.BATTERY_SCAN, OnboardingStep.BATTERY_DETAIL.restored(false))
        assertEquals(OnboardingStep.BATTERY_CHECK, OnboardingStep.BATTERY_CHECK.restored(true))
        assertEquals(OnboardingStep.COMPLETE, OnboardingStep.COMPLETE.restored(false))
    }

    @Test fun `empty scan reports zero and selection excludes unchecked downloads`() {
        val result = FirstRunJunkResult(mutableListOf())
        assertEquals(0L, result.selectedBytes)
        val cache = FirstRunJunkItem("cache", listOf(FirstRunJunkTarget("/cache", 12, FirstRunDeleteMode.FILE)), true)
        val download = FirstRunJunkItem("download", listOf(FirstRunJunkTarget("/download", 50, FirstRunDeleteMode.FILE)), false)
        result.groups += FirstRunJunkGroup(FirstRunJunkCategory.SYSTEM_CACHE, mutableListOf(cache))
        result.groups += FirstRunJunkGroup(FirstRunJunkCategory.DOWNLOADS, mutableListOf(download))
        assertEquals(12L, result.selectedBytes)
        assertEquals(2, result.scannedItems)
        download.selected = true
        assertEquals(62L, result.selectedBytes)
        cache.selected = false
        assertEquals(50L, result.selectedBytes)
    }
}
