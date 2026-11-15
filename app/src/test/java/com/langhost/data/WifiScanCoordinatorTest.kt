package com.langhost.data

import com.langhost.data.model.WifiNetwork
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiScanCoordinatorTest {
    private val sample = WifiNetwork(
        ssid = "Test Wi-Fi",
        bssid = "02:11:22:33:44:55",
        signalDbm = -48,
        frequencyMhz = 2437,
        channel = 6,
        security = "WPA2"
    )

    @Test
    fun broadcastReturnsResultsAndUnregistersReceiverOnce() = runBlocking {
        val platform = FakeWifiScanPlatform(listOf(sample))
        val pending = async { WifiScanCoordinator(platform).scan() }
        yield()
        platform.emit(updated = true)
        platform.emit(updated = false)
        val batch = pending.await()
        assertEquals(listOf(sample), batch.results)
        assertTrue(batch.updated)
        assertEquals(1, platform.registrationCount)
        assertEquals(1, platform.unregisterCount)
        assertEquals(1, platform.startCount)
    }

    @Test
    fun rejectedScanReturnsCachedResultsWithoutMarkingThemFresh() = runBlocking {
        val platform = FakeWifiScanPlatform(listOf(sample), startScanResult = false)
        val batch = WifiScanCoordinator(platform).scan()
        assertEquals(listOf(sample), batch.results)
        assertFalse(batch.updated)
        assertEquals(1, platform.unregisterCount)
    }

    @Test
    fun timeoutReturnsCachedResultsAndReleasesReceiver() = runBlocking {
        val platform = FakeWifiScanPlatform(listOf(sample))
        val batch = WifiScanCoordinator(platform, timeoutMs = 25L).scan()
        assertEquals(listOf(sample), batch.results)
        assertFalse(batch.updated)
        assertEquals(1, platform.unregisterCount)
    }

    @Test
    fun cancellationReleasesReceiver() = runBlocking {
        val platform = FakeWifiScanPlatform(listOf(sample))
        val pending = async { WifiScanCoordinator(platform).scan() }
        yield()
        pending.cancelAndJoin()
        assertEquals(1, platform.unregisterCount)
    }

    private class FakeWifiScanPlatform(
        private val cached: List<WifiNetwork>,
        private val startScanResult: Boolean = true
    ) : WifiScanPlatform {
        private var callback: ((Boolean) -> Unit)? = null
        var registrationCount = 0
            private set
        var unregisterCount = 0
            private set
        var startCount = 0
            private set
        override fun registerScanResultsReceiver(
            onResultsAvailable: (updated: Boolean) -> Unit
        ): WifiScanRegistration {
            registrationCount++
            callback = onResultsAvailable
            return WifiScanRegistration { unregisterCount++ }
        }
        override fun startScan(): Boolean {
            startCount++
            return startScanResult
        }
        override fun scanResults(): List<WifiNetwork> = cached
        fun emit(updated: Boolean) { callback?.invoke(updated) }
    }
}
