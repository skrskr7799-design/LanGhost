package com.langhost.data

import com.langhost.data.model.Device
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLifecycleEngineTest {
    private val engine = DeviceLifecycleEngine()
    private val now = 1_000_000L

    @Test
    fun retainsPreviouslyKnownDeviceAsOfflineWithinRetentionWindow() {
        val cached = device(ip = "192.168.1.20", mac = "AA:00:00:00:00:20", lastSeen = now - 15 * 60_000L)

        val result = engine.reconcile(
            discoveredOnline = emptyList(),
            cachedDevices = listOf(cached),
            networkScope = SCOPE,
            now = now
        )

        assertEquals(listOf(cached), result)
        assertTrue(now - result.single().lastSeen >= DeviceLifecycleEngine.DEFAULT_ONLINE_WINDOW_MS)
    }

    @Test
    fun onlineSnapshotReplacesCachedSnapshotForSameStableIdentity() {
        val old = device(
            ip = "192.168.1.20",
            mac = "AA:00:00:00:00:20",
            lastSeen = now - 20 * 60_000L,
            nickname = "Living room"
        )
        val fresh = old.copy(
            ip = "192.168.1.21",
            lastSeen = now,
            openPorts = listOf(22, 443),
            isFavorite = true
        )

        val result = engine.reconcile(
            discoveredOnline = listOf(fresh),
            cachedDevices = listOf(old),
            networkScope = SCOPE,
            now = now
        )

        assertEquals(1, result.size)
        assertEquals(fresh, result.single())
        assertEquals(listOf(22, 443), result.single().openPorts)
        assertTrue(result.single().isFavorite)
    }

    @Test
    fun neverMergesDevicesFromAnotherNetworkScope() {
        val sameMacOtherNetwork = device(
            ip = "192.168.1.20",
            mac = "AA:00:00:00:00:20",
            scope = "other-network",
            lastSeen = now
        )

        val result = engine.reconcile(
            discoveredOnline = listOf(sameMacOtherNetwork),
            cachedDevices = listOf(sameMacOtherNetwork),
            networkScope = SCOPE,
            now = now
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun duplicateStableIdsResolveDeterministicallyToNewestSnapshot() {
        val older = device(
            ip = "192.168.1.20",
            mac = "AA:00:00:00:00:20",
            lastSeen = now - 1_000L,
            nickname = "older"
        )
        val newer = older.copy(ip = "192.168.1.21", lastSeen = now, nickname = "newer")

        val first = engine.reconcile(
            discoveredOnline = listOf(older, newer),
            cachedDevices = emptyList(),
            networkScope = SCOPE,
            now = now
        )
        val reversed = engine.reconcile(
            discoveredOnline = listOf(newer, older),
            cachedDevices = emptyList(),
            networkScope = SCOPE,
            now = now
        )

        assertEquals(1, first.size)
        assertEquals(newer, first.single())
        assertEquals(first, reversed)
    }

    @Test
    fun excludesOfflineDevicesOutsideRetentionWindow() {
        val expired = device(
            ip = "192.168.1.20",
            mac = "AA:00:00:00:00:20",
            lastSeen = now - DeviceLifecycleEngine.DEFAULT_OFFLINE_RETENTION_MS
        )

        val result = engine.reconcile(
            discoveredOnline = emptyList(),
            cachedDevices = listOf(expired),
            networkScope = SCOPE,
            now = now
        )

        assertTrue(result.isEmpty())
    }

    private fun device(
        ip: String,
        mac: String,
        scope: String = SCOPE,
        lastSeen: Long,
        nickname: String = ""
    ) = Device(
        ip = ip,
        mac = mac,
        networkScope = scope,
        lastSeen = lastSeen,
        firstSeen = 10L,
        nickname = nickname
    )

    private companion object {
        const val SCOPE = "home-network"
    }
}
