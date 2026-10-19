package com.nice.aceclean.ad

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackInterstitialExitTest {
    @Test fun unavailableAdExitsImmediately() = runBlocking {
        var finished = 0
        val exit = BackInterstitialExit({}, { finished++ }, { throw AssertionError(it) })
        exit.request()
        assertEquals(1, finished)
    }

    @Test fun displayFailureStillExits() = runBlocking {
        var finished = 0
        var failures = 0
        val exit = BackInterstitialExit(
            { throw IllegalStateException("Ad display failed") },
            { finished++ },
            { failures++ },
        )
        exit.request()
        assertEquals(1, failures)
        assertEquals(1, finished)
    }

    @Test fun repeatedBackWhileAdIsOpenWaitsForCloseAndExitsOnce() = runBlocking {
        val closed = CompletableDeferred<Unit>()
        var attempts = 0
        var finished = 0
        val exit = BackInterstitialExit(
            { attempts++; closed.await() },
            { finished++ },
            { throw AssertionError(it) },
        )
        val first = launch { exit.request() }
        yield()
        assertTrue(exit.requested)
        exit.request()
        assertEquals(1, attempts)
        assertEquals(0, finished)
        closed.complete(Unit)
        first.join()
        exit.request()
        assertEquals(1, attempts)
        assertEquals(1, finished)
    }

    @Test fun destroyedActivityCancelsPendingExitWithoutFinishingTheOldInstance() = runBlocking {
        val closed = CompletableDeferred<Unit>()
        var finished = 0
        var failures = 0
        val exit = BackInterstitialExit({ closed.await() }, { finished++ }, { failures++ })
        val job = launch { exit.request() }
        yield()
        job.cancel()
        job.join()
        assertEquals(0, finished)
        assertEquals(0, failures)
    }
}
