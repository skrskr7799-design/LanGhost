package com.langhost.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTest {

    @Test
    fun stableIdUsesMacAndLogicalNetworkNotAccessPoint() {
        val firstAp = Device(ip = "192.168.1.20", mac = "aa:bb:cc:dd:ee:ff", networkBssid = "11:22:33:44:55:66", networkScope = "lan-a")
        val secondAp = firstAp.copy(networkBssid = "22:33:44:55:66:77")

        assertEquals("AA:BB:CC:DD:EE:FF@lan-a", firstAp.stableId)
        assertEquals(firstAp.stableId, secondAp.stableId)
    }

    @Test
    fun stableIdScopesMacAndIpToNetworkWhenAvailable() {
        assertEquals("AA:BB:CC:DD:EE:FF", Device(ip = "192.168.1.20", mac = "aa:bb:cc:dd:ee:ff").stableId)
        assertEquals("AA:BB:CC:DD:EE:FF@lan-a", Device(ip = "192.168.1.20", mac = "aa:bb:cc:dd:ee:ff", networkScope = "lan-a").stableId)
        assertEquals("ip:192.168.1.20", Device(ip = "192.168.1.20").stableId)
        assertEquals("ip:lan-a:192.168.1.20", Device(ip = "192.168.1.20", networkScope = "lan-a").stableId)
    }

    @Test
    fun displayNameUsesMostSpecificAvailableLabel() {
        assertEquals("Office PC", Device(ip = "192.168.1.5", hostname = "desktop", nickname = "Office PC").displayName)
        assertEquals("desktop", Device(ip = "192.168.1.5", hostname = "desktop", vendor = "Vendor").displayName)
        assertEquals("Vendor", Device(ip = "192.168.1.5", vendor = "Vendor").displayName)
    }

    @Test
    fun detectsLocallyAdministeredMacAddresses() {
        assertTrue(Device.isRandomMacAddress("02:11:22:33:44:55"))
        assertTrue(Device.isRandomMacAddress("06:11:22:33:44:55"))
        assertTrue(Device.isRandomMacAddress("03:11:22:33:44:55"))
        assertTrue(Device.isRandomMacAddress("07:11:22:33:44:55"))
        assertTrue(Device.isRandomMacAddress("0B:11:22:33:44:55"))
        assertTrue(Device.isRandomMacAddress("0F:11:22:33:44:55"))
        assertFalse(Device.isRandomMacAddress("00:11:22:33:44:55"))
        assertFalse(Device.isRandomMacAddress(""))
    }

    @Test
    fun endpointIdentityRejectsIpChangesEvenWhenDeviceIdentityIsStable() {
        val original = Device(
            ip = "192.168.1.10", mac = "AA:BB:CC:DD:EE:FF", networkScope = "home"
        )
        val sameEndpoint = original.copy(lastSeen = original.lastSeen + 1)
        val changedIp = original.copy(ip = "192.168.1.11")
        val changedNetwork = original.copy(networkScope = "office")

        assertTrue(original.hasSameEndpointAs(sameEndpoint))
        assertFalse(original.hasSameEndpointAs(changedIp))
        assertFalse(original.hasSameEndpointAs(changedNetwork))
    }

    @Test
    fun manualPortUpdatesPreserveDedicatedSecurityAuditEvidence() {
        val device = Device(
            ip = "192.168.1.10",
            openPorts = listOf(22, 80),
            auditedPorts = listOf(23)
        )

        val updated = device.withUpdatedPorts(listOf(443, 8080))

        assertEquals(listOf(443, 8080), updated.openPorts)
        assertEquals(listOf(23), updated.auditedPorts)
        assertTrue(updated.portsScannedAt > 0L)
        assertEquals(0L, device.portsScannedAt)
    }

    @Test
    fun emptyCompletedPortScanIsDistinguishableFromNeverScanned() {
        val neverScanned = Device(ip = "192.168.1.10")
        val completedEmptyScan = neverScanned.withUpdatedPorts(emptyList())

        assertEquals(0L, neverScanned.portsScannedAt)
        assertTrue(completedEmptyScan.portsScannedAt > 0L)
        assertTrue(completedEmptyScan.openPorts.isEmpty())
    }

    @Test
    fun arpObservationAloneDoesNotMakeDeviceOnline() {
        val observedAt = System.currentTimeMillis()
        val device = Device(
            ip = "192.168.1.40",
            mac = "AA:BB:CC:DD:EE:40",
            lastSeen = observedAt,
            lastConfirmedOnline = 0L,
            discoveryEvidence = DiscoveryEvidence.ARP_OBSERVATION
        )

        assertFalse(device.isOnline)
    }

    @Test
    fun deviceClassificationUsesKnownSignals() {
        assertEquals(DeviceType.ROUTER, Device.guessType(emptyList(), "", "", true))
        assertEquals(DeviceType.PRINTER, Device.guessType(listOf(9100), "", "", false))
        assertEquals("Windows (RDP)", Device.guessOs(listOf(3389), "", ""))
    }
}
