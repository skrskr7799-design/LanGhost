package com.langhost.data

import com.langhost.data.model.DiscoverySourceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ArpTableParserTest {
    @Test
    fun includesOnlyEntriesFromActiveInterface() {
        val arp = """IP address       HW type     Flags       HW address            Mask     Device
192.168.1.10     0x1         0x2         aa:bb:cc:dd:ee:01     *        wlan0
192.168.1.11     0x1         0x2         aa:bb:cc:dd:ee:02     *        eth0
192.168.1.12     0x1         0x0         00:00:00:00:00:00     *        wlan0
192.168.1.13     0x1         0x0         aa:bb:cc:dd:ee:03     *        wlan0
192.168.1.14     0x1         0x2         01:00:5e:00:00:01     *        wlan0
192.168.1.15     0x1         0x2         ff:ff:ff:ff:ff:ff     *        wlan0
"""

        assertEquals(
            mapOf("192.168.1.10" to "AA:BB:CC:DD:EE:01"),
            parseArpTable(arp, "wlan0")
        )
    }

    @Test
    fun unknownInterfaceDoesNotImportUnscopedArpEntries() {
        val arp = """IP address       HW type     Flags       HW address            Mask     Device
192.168.1.10     0x1         0x2         aa:bb:cc:dd:ee:01     *        wlan0
"""

        assertEquals(emptyMap<String, String>(), parseArpTable(arp, ""))
    }

    @Test
    fun readableEmptyArpTableMeansNoResponses() {
        assertEquals(
            DiscoverySourceStatus.NO_RESPONSES,
            arpSourceStatus(available = true, entries = emptyMap())
        )
    }

    @Test
    fun unreadableArpTableMeansUnavailableEvenWhenEntriesAreEmpty() {
        assertEquals(
            DiscoverySourceStatus.UNAVAILABLE,
            arpSourceStatus(available = false, entries = emptyMap())
        )
    }

    @Test
    fun readableArpTableWithEntriesMeansResponded() {
        assertEquals(
            DiscoverySourceStatus.RESPONDED,
            arpSourceStatus(
                available = true,
                entries = mapOf("192.168.1.10" to "AA:BB:CC:DD:EE:01")
            )
        )
    }
}
