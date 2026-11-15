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
        var probeCalls = 0
        val result = HostDiscovery.discover(
            candidates = listOf("192.168.1.2", "192.168.1.3"),
            localIp = "192.168.1.2",
            maxConcurrency = 2,
            timeoutMs = 150
        ) { _, _ ->
            probeCalls++
            true
        }

        assertEquals(setOf("192.168.1.2", "192.168.1.3"), result)
        assertEquals(1, probeCalls)
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
}
