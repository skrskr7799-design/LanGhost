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
    fun pingHistoryRetentionKeepsMostRecentlyUpdatedIps() {
        assertEquals(
            setOf("ping_b", "ping_c"),
            retainPingHistoryKeys(mapOf("ping_a" to 10L, "ping_b" to 30L, "ping_c" to 20L), 2)
        )
    }
}
