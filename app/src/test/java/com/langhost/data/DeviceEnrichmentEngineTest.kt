package com.langhost.data

import com.langhost.data.model.Device
import com.langhost.data.model.DeviceType
import com.langhost.data.model.DiscoveryEvidence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEnrichmentEngineTest {
    @Test
    fun restoresCustomizationAndPreservesExistingEvidence() = runBlocking {
        val engine = DeviceEnrichmentEngine(
            loadCustomization = { id, legacy ->
                assertEquals("AA:BB:CC:DD:EE:01@scope-new", id)
                assertTrue(legacy.contains("AA:BB:CC:DD:EE:01@scope-old"))
                DeviceCustomization(
                    nickname = "Living room",
                    notes = "NAS",
                    isFavorite = true,
                    isWatched = true,
                    firstSeen = 100L
                )
            },
            resolveHostname = { "nas.local" },
            lookupVendor = { "Synology" }
        )
        val existing = Device(
            ip = "192.168.1.20",
            mac = "AA:BB:CC:DD:EE:01",
            networkScope = "scope-new",
            firstSeen = 90L,
            openPorts = listOf(22, 445),
            portsScannedAt = 150L,
            auditedPorts = listOf(445),
            httpFingerprint = "Server: test",
            tags = listOf("storage")
        )

        val result = engine.enrich(
            ip = "192.168.1.20",
            mac = existing.mac,
            bssid = "11:22:33:44:55:66",
            networkScope = "scope-new",
            legacyNetworkScope = "scope-old",
            localIp = "192.168.1.2",
            gateway = "192.168.1.1",
            hasTcpResponse = true,
            existing = null,
            existingByStableId = existing,
            now = 200L
        )

        assertEquals("Living room", result.nickname)
        assertEquals("NAS", result.notes)
        assertTrue(result.isFavorite)
        assertTrue(result.isWatched)
        assertEquals(100L, result.firstSeen)
        assertEquals(listOf(22, 445), result.openPorts)
        assertEquals(150L, result.portsScannedAt)
        assertEquals(listOf(445), result.auditedPorts)
        assertEquals("Server: test", result.httpFingerprint)
        assertEquals(listOf("storage"), result.tags)
        assertEquals(DeviceType.NAS, result.deviceType)
        assertEquals(200L, result.lastConfirmedOnline)
        assertEquals(DiscoveryEvidence.TCP_RESPONSE, result.discoveryEvidence)
    }

    @Test
    fun identifiesRandomMacAndUsesCachedHostnameWhenProbeIsEmpty() = runBlocking {
        val engine = DeviceEnrichmentEngine(
            loadCustomization = { _, _ -> DeviceCustomization() },
            resolveHostname = { "" },
            lookupVendor = { "must-not-be-used" }
        )
        val existing = Device(
            ip = "192.168.1.30",
            mac = "02:AA:BB:CC:DD:EE",
            hostname = "cached-device",
            vendor = "Cached vendor",
            networkScope = "scope",
            firstSeen = 50L
        )

        val result = engine.enrich(
            ip = existing.ip,
            mac = existing.mac,
            bssid = "",
            networkScope = "scope",
            legacyNetworkScope = "scope",
            localIp = existing.ip,
            gateway = "192.168.1.1",
            existing = existing,
            existingByStableId = existing,
            now = 100L
        )

        assertTrue(result.isRandomMac)
        assertEquals("Random MAC", result.vendor)
        assertEquals("cached-device", result.hostname)
        assertTrue(result.isLocalDevice)
        assertFalse(result.isGateway)
        assertEquals(50L, result.firstSeen)
        assertEquals(0L, result.portsScannedAt)
        assertEquals(100L, result.lastConfirmedOnline)
        assertEquals(DiscoveryEvidence.LOCAL_INTERFACE, result.discoveryEvidence)
    }

    @Test
    fun arpObservationDoesNotBecomeFreshReachabilityConfirmation() = runBlocking {
        val engine = DeviceEnrichmentEngine(
            loadCustomization = { _, _ -> DeviceCustomization() },
            resolveHostname = { "" },
            lookupVendor = { "Vendor" }
        )
        val result = engine.enrich(
            ip = "192.168.1.40",
            mac = "AA:BB:CC:DD:EE:40",
            bssid = "",
            networkScope = "scope",
            legacyNetworkScope = "scope",
            localIp = "192.168.1.2",
            gateway = "192.168.1.1",
            hasTcpResponse = false,
            existing = null,
            existingByStableId = null,
            now = 500L
        )

        assertEquals(500L, result.lastSeen)
        assertEquals(0L, result.lastConfirmedOnline)
        assertEquals(DiscoveryEvidence.ARP_OBSERVATION, result.discoveryEvidence)
    }

    @Test
    fun migratesOnlyThePreviousNetworkScopedCustomizationKey() = runBlocking {
        var requestedId = ""
        var requestedLegacyIds = emptyList<String>()
        val engine = DeviceEnrichmentEngine(
            loadCustomization = { id, legacyIds ->
                requestedId = id
                requestedLegacyIds = legacyIds
                DeviceCustomization(nickname = "legacy scoped name")
            },
            resolveHostname = { "" },
            lookupVendor = { "" }
        )

        val result = engine.enrich(
            ip = "10.0.0.8",
            mac = "",
            bssid = "",
            networkScope = "new-scope",
            legacyNetworkScope = "old-scope",
            localIp = "10.0.0.2",
            gateway = "10.0.0.1",
            existing = null,
            existingByStableId = null,
            now = 100L
        )

        assertEquals("ip:new-scope:10.0.0.8", requestedId)
        assertEquals(listOf("ip:old-scope:10.0.0.8"), requestedLegacyIds)
        assertEquals("legacy scoped name", result.nickname)
    }
}
