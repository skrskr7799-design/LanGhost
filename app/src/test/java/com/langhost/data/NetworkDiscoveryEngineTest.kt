package com.langhost.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiscoveryEngineTest {
    @Test
    fun mergesPositiveProbeArpLocalAndGatewayEvidenceWithinSubnet() = runBlocking {
        val probed = mutableListOf<String>()
        val engine = NetworkDiscoveryEngine(
            probeHost = { ip, _ ->
                probed += ip
                ip == "192.168.10.3"
            },
            ensureCurrentNetworkScope = {}
        )

        val result = engine.discover(
            localIp = "192.168.10.2",
            gateway = "192.168.10.1",
            netmask = "255.255.255.248",
            arpEntries = mapOf(
                "192.168.10.4" to "AA:BB:CC:DD:EE:04",
                "192.168.20.4" to "AA:BB:CC:DD:EE:20"
            ),
            networkScope = "scope-a",
            timeoutMs = 150,
            maxTargets = 16,
            maxConcurrency = 2
        )

        assertEquals(
            setOf("192.168.10.1", "192.168.10.2", "192.168.10.3", "192.168.10.4"),
            result.ips
        )
        assertEquals(mapOf("192.168.10.4" to "AA:BB:CC:DD:EE:04"), result.arpAddresses)
        assertTrue(probed.isNotEmpty())
        assertEquals("192.168.10.0/29", result.coverage.subnetCidr)
        assertEquals(6, result.coverage.checkedAddresses)
        assertEquals(6L, result.coverage.totalUsableAddresses)
        assertFalse(result.coverage.truncated)
        assertEquals("scope-a", result.coverage.networkScope)
    }

    @Test
    fun candidateLimitIsReflectedInCoverageWithoutUnboundedExpansion() = runBlocking {
        var probeCount = 0
        val result = NetworkDiscoveryEngine(
            probeHost = { _, _ ->
                probeCount++
                false
            },
            ensureCurrentNetworkScope = {}
        ).discover(
            localIp = "10.0.0.10",
            gateway = "10.0.0.1",
            netmask = "255.255.0.0",
            arpEntries = emptyMap(),
            networkScope = "scope-large",
            timeoutMs = 150,
            maxTargets = 12,
            maxConcurrency = 3
        )

        assertEquals(12, probeCount)
        assertEquals(12, result.coverage.checkedAddresses)
        assertEquals(65_534L, result.coverage.totalUsableAddresses)
        assertTrue(result.coverage.truncated)
        assertTrue(result.ips.contains("10.0.0.10"))
        assertTrue(result.ips.contains("10.0.0.1"))
    }

    @Test
    fun invalidSubnetDoesNotInventCandidatesButKeepsKnownEndpoints() = runBlocking {
        var probeCount = 0
        val result = NetworkDiscoveryEngine(
            probeHost = { _, _ ->
                probeCount++
                true
            },
            ensureCurrentNetworkScope = {}
        ).discover(
            localIp = "192.168.1.10",
            gateway = "192.168.1.1",
            netmask = "0.0.0.0",
            arpEntries = mapOf("192.168.1.20" to "AA:BB:CC:DD:EE:20"),
            networkScope = "scope-invalid",
            timeoutMs = 150,
            maxTargets = 100,
            maxConcurrency = 4
        )

        assertEquals(0, probeCount)
        assertEquals(setOf("192.168.1.10", "192.168.1.1"), result.ips)
        assertTrue(result.arpAddresses.isEmpty())
        assertEquals(0, result.coverage.checkedAddresses)
        assertEquals(0L, result.coverage.totalUsableAddresses)
        assertFalse(result.coverage.truncated)
    }

    @Test
    fun scopeCancellationPropagatesBeforeNextProbeBatch() = runBlocking {
        var checks = 0
        var probes = 0
        val engine = NetworkDiscoveryEngine(
            probeHost = { _, _ ->
                probes++
                true
            },
            ensureCurrentNetworkScope = {
                checks++
                if (checks == 2) throw CancellationException("network changed")
            }
        )

        try {
            engine.discover(
                localIp = "192.168.1.10",
                gateway = "192.168.1.1",
                netmask = "255.255.255.0",
                arpEntries = emptyMap(),
                networkScope = "scope-a",
                timeoutMs = 150,
                maxTargets = 10,
                maxConcurrency = 2
            )
            throw AssertionError("Expected network-scope cancellation")
        } catch (_: CancellationException) {
            assertEquals(2, checks)
            assertEquals(2, probes)
        }
    }
}
