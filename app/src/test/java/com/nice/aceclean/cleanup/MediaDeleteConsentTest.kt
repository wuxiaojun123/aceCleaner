package com.nice.aceclean.cleanup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDeleteConsentTest {
    @Test
    fun `a callback without a launched request cannot complete deletion`() {
        val consent = MediaDeleteConsent()
        assertFalse(consent.complete())
        assertTrue(consent.claimRequest())
        assertTrue(consent.complete())
    }

    @Test
    fun `returning to the resumed collector does not launch a second sheet`() {
        val consent = MediaDeleteConsent()
        assertTrue(consent.claimRequest())
        assertFalse(consent.claimRequest())
        assertTrue(consent.complete())
        assertFalse(consent.claimRequest())
        assertFalse(consent.complete())
    }

    @Test
    fun `a cancelled or failed request stays consumed and a new session can retry`() {
        val previous = MediaDeleteConsent()
        assertTrue(previous.claimRequest())
        assertTrue(previous.complete())
        assertFalse(previous.complete())
        assertFalse(previous.claimRequest())
        assertTrue(MediaDeleteConsent().claimRequest())
    }
}
