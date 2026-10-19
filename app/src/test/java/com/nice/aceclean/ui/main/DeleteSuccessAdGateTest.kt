package com.nice.aceclean.ui.main

import com.nice.aceclean.ui.main.DeleteSuccessAdGate.Action.*
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteSuccessAdGateTest {
    @Test fun shownEntryAdMakesBackReturnWithoutAnotherAd() {
        val gate = DeleteSuccessAdGate()
        assertEquals(SHOW_ENTRY, gate.onEnter())
        gate.onShown()
        assertEquals(NONE, gate.onAttemptFinished())
        assertEquals(RETURN_HOME, gate.onBack())
        assertEquals(NONE, gate.onBack())
    }

    @Test fun failedEntryRetriesOnBackThenReturnsEvenIfThatAttemptAlsoFails() {
        val gate = DeleteSuccessAdGate()
        gate.onEnter()
        assertEquals(NONE, gate.onAttemptFinished())
        assertEquals(SHOW_BACK, gate.onBack())
        assertEquals(NONE, gate.onBack())
        assertEquals(RETURN_HOME, gate.onAttemptFinished())
        assertEquals(NONE, gate.onBack())
    }

    @Test fun backDuringEntryLoadWaitsAndRetriesOnlyAfterEntryFails() {
        val gate = DeleteSuccessAdGate()
        gate.onEnter()
        assertEquals(NONE, gate.onBack())
        assertEquals(NONE, gate.onBack())
        assertEquals(SHOW_BACK, gate.onAttemptFinished())
        gate.onShown()
        assertEquals(RETURN_HOME, gate.onAttemptFinished())
    }

    @Test fun pendingBackDoesNotRetryIfEntryEventuallyShows() {
        val gate = DeleteSuccessAdGate()
        gate.onEnter()
        gate.onBack()
        gate.onShown()
        assertEquals(RETURN_HOME, gate.onAttemptFinished())
    }

    @Test fun shownImpressionSuppressesFallbackEvenWhenShowLaterReportsFailure() {
        val gate = DeleteSuccessAdGate()
        gate.onEnter()
        gate.onShown()
        gate.onAttemptFinished()
        assertEquals(RETURN_HOME, gate.onBack())
    }

    @Test fun restoredPageKeepsShownStateAndDoesNotRepeatEntry() {
        val gate = DeleteSuccessAdGate(entryAttempted = true, shown = true)
        assertEquals(NONE, gate.onEnter())
        assertEquals(RETURN_HOME, gate.onBack())
    }

    @Test fun restoringPendingBackResumesTheFallbackWithoutRepeatingEntry() {
        val gate = DeleteSuccessAdGate(entryAttempted = true, backRequested = true)
        assertEquals(SHOW_BACK, gate.onEnter())
        assertEquals(RETURN_HOME, gate.onAttemptFinished())
    }
}
