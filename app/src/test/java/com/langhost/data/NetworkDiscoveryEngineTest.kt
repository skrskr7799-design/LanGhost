package com.langhost.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NetworkDiscoveryEngineTest {
    @Test
    fun mergesPositiveProbeArpLocalAndGatewayEvidenceWithinSubnet() = runBlocking {
        val probeCount = AtomicInteger(0)
        val engine = NetworkDiscoveryEngine(
            probeHost = { ip, _ ->
                probeCount.incrementAndGet()
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
        assertEquals(setOf("192.168.10.3"), result.tcpResponsiveIps)
        assertTrue(probeCount.get() > 0)
        assertEquals("192.168.10.0/29", result.coverage.subnetCidr)
        assertEquals(6, result.coverage.checkedAddresses)
        assertEquals(6L, result.coverage.totalUsableAddresses)
        assertFalse(result.coverage.truncated)
        assertEquals("scope-a", result.coverage.networkScope)
    }

    @Test
    fun candidateLimitIsReflectedInCoverageWithoutUnboundedExpansion() = runBlocking {
        val probeCount = AtomicInteger(0)
        val result = NetworkDiscoveryEngine(
            probeHost = { _, _ ->
                probeCount.incrementAndGet()
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

        assertEquals(11, probeCount.get())
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
            netmask = "255.0.255.0",
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
            assertEquals(1, probes)
        }
    }

    @Test
    fun refreshesArpAfterProbingAndIncludesHostsWithoutOpenProbePorts() = runBlocking {
        val probeCount = AtomicInteger(0)
        var arpRefreshCount = 0
        val result = NetworkDiscoveryEngine(
            probeHost = { ip, _ ->
                probeCount.incrementAndGet()
                false
            },
            ensureCurrentNetworkScope = {}
        ).discover(
            localIp = "192.168.10.2",
            gateway = "192.168.10.1",
            netmask = "255.255.255.248",
            arpEntries = emptyMap(),
            networkScope = "scope-refresh",
            timeoutMs = 150,
            maxTargets = 16,
            maxConcurrency = 2,
            refreshArpEntries = {
                assertTrue("ARP refresh must run after candidate probes", probeCount.get() > 0)
                arpRefreshCount++
                mapOf(
                    "192.168.10.4" to "AA:BB:CC:DD:EE:04",
                    "192.168.20.4" to "AA:BB:CC:DD:EE:20"
                )
            }
        )

        assertEquals(1, arpRefreshCount)
        assertTrue(result.ips.contains("192.168.10.4"))
        assertTrue(result.tcpResponsiveIps.isEmpty())
        assertEquals("AA:BB:CC:DD:EE:04", result.arpAddresses["192.168.10.4"])
        assertFalse(result.ips.contains("192.168.20.4"))
    }

    @Test
    fun networkScopeIsCheckedBeforeRefreshedArpRead() = runBlocking {
        var checks = 0
        var refreshes = 0
        val engine = NetworkDiscoveryEngine(
            probeHost = { _, _ -> false },
            ensureCurrentNetworkScope = {
                checks++
                if (checks == 4) throw CancellationException("network changed before ARP refresh")
            }
        )

        try {
            engine.discover(
                localIp = "192.168.10.2",
                gateway = "192.168.10.1",
                netmask = "255.255.255.248",
                arpEntries = emptyMap(),
                networkScope = "scope-refresh",
                timeoutMs = 150,
                maxTargets = 16,
                maxConcurrency = 2,
                refreshArpEntries = {
                    refreshes++
                    emptyMap()
                }
            )
            throw AssertionError("Expected network-scope cancellation")
        } catch (_: CancellationException) {
            assertEquals(0, refreshes)
        }
    }

    @Test
    fun networkScopeIsRecheckedAfterRefreshedArpRead() = runBlocking {
        var checks = 0
        var refreshes = 0
        val engine = NetworkDiscoveryEngine(
            probeHost = { _, _ -> false },
            ensureCurrentNetworkScope = {
                checks++
                if (checks == 5) throw CancellationException("network changed during ARP refresh")
            }
        )

        try {
            engine.discover(
                localIp = "192.168.10.2",
                gateway = "192.168.10.1",
                netmask = "255.255.255.248",
                arpEntries = emptyMap(),
                networkScope = "scope-refresh",
                timeoutMs = 150,
                maxTargets = 16,
                maxConcurrency = 2,
                refreshArpEntries = {
                    refreshes++
                    emptyMap()
                }
            )
            throw AssertionError("Expected network-scope cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, refreshes)
        }
    }

    @Test
    fun includesOnlyInSubnetDnsSdServiceObservations() = runBlocking {
        val result = NetworkDiscoveryEngine(
            probeHost = { _, _ -> false },
            ensureCurrentNetworkScope = {}
        ).discover(
            localIp = "192.168.10.2",
            gateway = "192.168.10.1",
            netmask = "255.255.255.248",
            arpEntries = emptyMap(),
            networkScope = "scope-nsd",
            timeoutMs = 150,
            maxTargets = 16,
            maxConcurrency = 2,
            discoverNsdServiceIps = {
                setOf("192.168.10.5", "192.168.20.5", "127.0.0.1")
            }
        )

        assertEquals(setOf("192.168.10.5"), result.nsdServiceIps)
        assertTrue(result.ips.contains("192.168.10.5"))
        assertFalse(result.ips.contains("192.168.20.5"))
        assertFalse(result.ips.contains("127.0.0.1"))
        assertTrue(result.tcpResponsiveIps.isEmpty())
    }

}
