package com.langhost.data

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HostDiscoveryTest {
    @Test
    fun includesLocalAddressWithoutCallingProbe() = runBlocking {
        val probeCalls = java.util.concurrent.atomic.AtomicInteger(0)
        val result = HostDiscovery.discover(
            candidates = listOf("192.168.1.2", "192.168.1.3"),
            localIp = "192.168.1.2",
            maxConcurrency = 2,
            timeoutMs = 150
        ) { _, _ ->
            probeCalls.incrementAndGet()
            true
        }

        assertEquals(setOf("192.168.1.2", "192.168.1.3"), result)
        assertEquals(1, probeCalls.get())
    }

    @Test
    fun neverExceedsConfiguredConcurrency() = runBlocking {
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val result = HostDiscovery.discover(
            candidates = (1..20).map { "192.168.1.$it" },
            localIp = "192.168.1.254",
            maxConcurrency = 4,
            timeoutMs = 150
        ) { _, _ ->
            val current = active.incrementAndGet()
            maximum.updateAndGet { old -> maxOf(old, current) }
            try {
                delay(10)
                true
            } finally {
                active.decrementAndGet()
            }
        }

        assertEquals(20, result.size)
        assertTrue(maximum.get() <= 4)
    }

    @Test
    fun ignoresOrdinaryProbeFailuresButKeepsSuccessfulHosts() = runBlocking {
        val result = HostDiscovery.discover(
            candidates = listOf("192.168.1.2", "192.168.1.3"),
            localIp = "192.168.1.1",
            maxConcurrency = 2,
            timeoutMs = 150
        ) { ip, _ ->
            if (ip.endsWith(".2")) error("simulated socket failure")
            true
        }

        assertEquals(setOf("192.168.1.3"), result)
    }

    @Test
    fun cancellationIsPropagated() = runBlocking {
        val job = launch {
            HostDiscovery.discover(
                candidates = (1..10).map { "192.168.1.$it" },
                localIp = "192.168.1.254",
                maxConcurrency = 2,
                timeoutMs = 150
            ) { _, _ ->
                delay(10_000)
                true
            }
        }
        delay(25)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
    @Test
    fun mapBoundedPreservesOrderAndRespectsConcurrencyLimit() = runBlocking {
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val result = HostDiscovery.mapBounded((1..25).toList(), 5) { value ->
            val current = active.incrementAndGet()
            maximum.updateAndGet { old -> maxOf(old, current) }
            try {
                delay(5)
                value * 2
            } finally {
                active.decrementAndGet()
            }
        }
        assertEquals((1..25).map { it * 2 }, result)
        assertTrue(maximum.get() <= 5)
    }

    @Test
    fun mapBoundedRejectsInvalidConcurrency() = runBlocking {
        try {
            HostDiscovery.mapBounded(listOf(1), 0) { it }
            throw AssertionError("Expected invalid concurrency to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
    @Test
    fun mapBoundedPropagatesCancellation() = runBlocking {
        val job = launch {
            HostDiscovery.mapBounded((1..10).toList(), 2) {
                delay(10_000)
                it
            }
        }
        delay(25)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun discoveryPassesClampedProbeBudget() = runBlocking {
        var receivedBudget = 0
        HostDiscovery.discover(
            candidates = listOf("192.168.1.10"),
            localIp = "192.168.1.1",
            maxConcurrency = 1,
            timeoutMs = 500
        ) { _, budget ->
            receivedBudget = budget
            true
        }
        assertEquals(1000, receivedBudget)
    }


    @Test
    fun discoveryChecksNetworkScopeBeforeEachBatchAndStopsWhenItChanges() = runBlocking {
        var batchChecks = 0
        val probeCalls = AtomicInteger(0)

        try {
            HostDiscovery.discover(
                candidates = (1..5).map { "192.168.1.$it" },
                localIp = "192.168.1.254",
                maxConcurrency = 1,
                timeoutMs = 150,
                beforeBatch = {
                    batchChecks++
                    if (batchChecks == 2) {
                        throw kotlinx.coroutines.CancellationException("network changed")
                    }
                }
            ) { _, _ ->
                probeCalls.incrementAndGet()
                true
            }
            throw AssertionError("Expected the network-scope checkpoint to cancel discovery")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertEquals(2, batchChecks)
            assertEquals(1, probeCalls.get())
        }
    }

    @Test
    fun mapBoundedRunsNetworkScopeCheckpointOncePerBatch() = runBlocking {
        var batchChecks = 0
        val result = HostDiscovery.mapBounded(
            items = (1..5).toList(),
            maxConcurrency = 2,
            beforeBatch = { batchChecks++ }
        ) { it * 2 }

        assertEquals((1..5).map { it * 2 }, result)
        assertEquals(5, batchChecks)
    }
 
    @Test
    fun discoveryRetriesOnlyHostsWithoutPositiveEvidence() = runBlocking {
        val attempts = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
        val result = HostDiscovery.discover(
            candidates = listOf("192.168.1.2", "192.168.1.3"),
            localIp = "192.168.1.1",
            maxConcurrency = 2,
            timeoutMs = 350
        ) { ip, budget ->
            assertTrue("Retry budget should remain bounded", budget <= 700)
            val count = attempts.computeIfAbsent(ip) { AtomicInteger() }.incrementAndGet()
            ip.endsWith(".2") || count >= 2
        }

        assertEquals(setOf("192.168.1.2", "192.168.1.3"), result)
        assertEquals(1, attempts["192.168.1.2"]?.get())
        assertEquals(2, attempts["192.168.1.3"]?.get())
    }

}
