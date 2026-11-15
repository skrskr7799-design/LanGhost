package com.langhost.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DataRetentionTest {
    @Test
    fun knownDeviceIdsAreDeduplicatedAndBoundedByRecency() {
        assertEquals(
            listOf("b", "c", "d"),
            retainKnownIds(listOf("a", "b", "b"), listOf("c", "d"), 3)
        )
        assertEquals(listOf("b", "a"), retainKnownIds(listOf("a", "b"), listOf("a"), 3))
    }

    @Test
    fun deviceCustomizationsArePrunedWithExpiredKnownIdentities() {
        val retainedIds = listOf("aa:bb:cc@home", "ip:home:192.168.1.10")
        val existingKeys = setOf(
            deviceCustomizationKeyName(retainedIds[0]),
            deviceCustomizationKeyName(retainedIds[1]),
            deviceCustomizationKeyName("old:device@old-network"),
            "unrelated_setting"
        )

        assertEquals(
            setOf(deviceCustomizationKeyName("old:device@old-network")),
            staleDeviceCustomizationKeys(existingKeys, retainedIds)
        )
    }

    @Test
    fun pingHistoryRetentionKeepsMostRecentlyUpdatedIps() {
        assertEquals(
            setOf("ping_b", "ping_c"),
            retainPingHistoryKeys(
                mapOf("ping_a" to 10L, "ping_b" to 30L, "ping_c" to 20L),
                maxKeys = 2,
                now = 30L,
                retentionMs = 30L
            )
        )
    }

    @Test
    fun pingHistoryRetentionDropsExpiredIps() {
        assertEquals(
            setOf("ping_recent"),
            retainPingHistoryKeys(
                mapOf("ping_expired" to 1L, "ping_recent" to 90L),
                maxKeys = 64,
                now = 100L,
                retentionMs = 20L
            )
        )
    }
}
