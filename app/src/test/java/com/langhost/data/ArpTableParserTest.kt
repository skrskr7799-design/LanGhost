package com.langhost.data

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
}
