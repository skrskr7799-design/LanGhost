package com.langhost.data

import com.langhost.data.model.Device
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ScanDiffTest {

    @Before
    fun resetBeforeTest() {
        ScanDiff.clear()
    }

    @After
    fun resetAfterTest() {
        ScanDiff.clear()
    }

    @Test
    fun detectsNewDevicesAndPortChanges() {
        val initial = Device(ip = "192.168.1.10", lastSeen = System.currentTimeMillis())
        val first = ScanDiff.updateAndDiff(listOf(initial))
        assertEquals(listOf(initial), first.appeared)

        val updated = initial.copy(openPorts = listOf(80), lastSeen = System.currentTimeMillis())
        val second = ScanDiff.updateAndDiff(listOf(updated))
        assertEquals(1, second.portsChanged.size)
        assertEquals(emptyList<Device>(), second.appeared)
    }

    @Test
    fun keepsSnapshotsSeparateAcrossNetworks() {
        val home = Device(ip = "192.168.1.10", mac = "aa:bb:cc:dd:ee:ff", networkScope = "home", lastSeen = System.currentTimeMillis())
        val office = Device(ip = "192.168.1.10", mac = "aa:bb:cc:dd:ee:ff", networkScope = "office", lastSeen = System.currentTimeMillis())

        ScanDiff.updateAndDiff(listOf(home), "home")
        ScanDiff.updateAndDiff(listOf(office), "office")

        val homeAgain = ScanDiff.updateAndDiff(listOf(home.copy(openPorts = listOf(80))), "home")
        assertEquals(emptyList<Device>(), homeAgain.appeared)
        assertEquals(emptyList<Device>(), homeAgain.disappeared)
        assertEquals(1, homeAgain.portsChanged.size)
    }

    @Test
    fun detectsDevicesMissingFromTheNextScan() {
        val device = Device(ip = "192.168.1.11", lastSeen = System.currentTimeMillis())
        ScanDiff.updateAndDiff(listOf(device))

        val result = ScanDiff.updateAndDiff(emptyList())

        assertEquals(listOf(device), result.disappeared)
    }
}
