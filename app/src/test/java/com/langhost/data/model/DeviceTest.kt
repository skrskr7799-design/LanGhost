package com.langhost.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTest {

    @Test
    fun stableIdUsesMacAndNetworkIdentity() {
        val device = Device(
            ip = "192.168.1.20",
            mac = "aa:bb:cc:dd:ee:ff",
            networkBssid = "11:22:33:44:55:66"
        )

        assertEquals("AA:BB:CC:DD:EE:FF@11:22:33:44:55:66", device.stableId)
    }

    @Test
    fun stableIdFallsBackToMacThenIp() {
        assertEquals("AA:BB:CC:DD:EE:FF", Device(ip = "192.168.1.20", mac = "aa:bb:cc:dd:ee:ff").stableId)
        assertEquals("ip:192.168.1.20", Device(ip = "192.168.1.20").stableId)
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
        assertFalse(Device.isRandomMacAddress("00:11:22:33:44:55"))
        assertFalse(Device.isRandomMacAddress(""))
    }

    @Test
    fun deviceClassificationUsesKnownSignals() {
        assertEquals(DeviceType.ROUTER, Device.guessType(emptyList(), "", "", true))
        assertEquals(DeviceType.PRINTER, Device.guessType(listOf(9100), "", "", false))
        assertEquals("Windows (RDP)", Device.guessOs(listOf(3389), "", ""))
    }
}
