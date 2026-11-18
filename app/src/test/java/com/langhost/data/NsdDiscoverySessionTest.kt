package com.langhost.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NsdDiscoverySessionTest {
    @Test
    fun successfulSessionStartsRunsWindowAndStopsExactlyOnce() = runBlocking {
        val events = mutableListOf<String>()
        val started = runNsdDiscoverySession(
            startDiscovery = { events += "start" },
            awaitStarted = { events += "started"; true },
            runDiscoveryWindow = { events += "window" },
            stopDiscovery = { events += "stop" },
            awaitStopped = { events += "stopped" },
            sessionTimeoutMs = 100,
            stopTimeoutMs = 100
        )

        assertTrue(started)
        assertEquals(listOf("start", "started", "window", "stop", "stopped"), events)
    }

    @Test
    fun failedStartSkipsWindowAndStopsExactlyOnce() = runBlocking {
        val stops = AtomicInteger()
        var windowRan = false
        val started = runNsdDiscoverySession(
            startDiscovery = {},
            awaitStarted = { false },
            runDiscoveryWindow = { windowRan = true },
            stopDiscovery = { stops.incrementAndGet() },
            awaitStopped = {},
            sessionTimeoutMs = 100,
            stopTimeoutMs = 100
        )

        assertFalse(started)
        assertFalse(windowRan)
        assertEquals(1, stops.get())
    }

    @Test
    fun startupTimeoutStopsExactlyOnce() = runBlocking {
        val stops = AtomicInteger()
        val started = runNsdDiscoverySession(
            startDiscovery = {},
            awaitStarted = { kotlinx.coroutines.awaitCancellation() },
            runDiscoveryWindow = { error("window must not run") },
            stopDiscovery = { stops.incrementAndGet() },
            awaitStopped = {},
            sessionTimeoutMs = 20,
            stopTimeoutMs = 100
        )

        assertFalse(started)
        assertEquals(1, stops.get())
    }

    @Test
    fun cancellationStillStopsAndPropagates() = runBlocking {
        val stops = AtomicInteger()
        val session = async {
            runNsdDiscoverySession(
                startDiscovery = {},
                awaitStarted = { true },
                runDiscoveryWindow = { kotlinx.coroutines.awaitCancellation() },
                stopDiscovery = { stops.incrementAndGet() },
                awaitStopped = {},
                sessionTimeoutMs = 1_000,
                stopTimeoutMs = 100
            )
        }
        yield()
        session.cancel()
        try {
            session.await()
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Expected.
        }

        assertEquals(1, stops.get())
    }

    @Test
    fun stopFailureDoesNotReplaceSuccessfulDiscoveryResult() = runBlocking {
        val stops = AtomicInteger()
        val started = runNsdDiscoverySession(
            startDiscovery = {},
            awaitStarted = { true },
            runDiscoveryWindow = {},
            stopDiscovery = { stops.incrementAndGet(); error("stop failed") },
            awaitStopped = {},
            sessionTimeoutMs = 100,
            stopTimeoutMs = 100
        )

        assertTrue(started)
        assertEquals(1, stops.get())
    }
}
