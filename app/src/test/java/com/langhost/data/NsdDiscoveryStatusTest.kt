package com.langhost.data

import com.langhost.data.model.DiscoverySourceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NsdDiscoveryStatusTest {
    @Test
    fun allServiceTypesFailMeansUnavailable() {
        assertEquals(
            DiscoverySourceStatus.UNAVAILABLE,
            nsdDiscoveryStatus(listOf(false, false), emptySet(), false, false, false)
        )
    }

    @Test
    fun mixedServiceTypeStartupMeansPartialEvenWhenSomeHostsRespond() {
        assertEquals(
            DiscoverySourceStatus.PARTIAL,
            nsdDiscoveryStatus(listOf(true, false, true), setOf("192.168.1.20"), true, false, false)
        )
    }

    @Test
    fun successfulStartupAndResolvedHostMeansResponded() {
        assertEquals(
            DiscoverySourceStatus.RESPONDED,
            nsdDiscoveryStatus(listOf(true, true), setOf("192.168.1.20"), true, false, false)
        )
    }

    @Test
    fun observedButUnresolvedServiceMeansPartial() {
        assertEquals(
            DiscoverySourceStatus.PARTIAL,
            nsdDiscoveryStatus(listOf(true), emptySet(), true, false, false)
        )
    }

    @Test
    fun resolutionFailureOrTrackingSaturationMeansPartial() {
        assertEquals(
            DiscoverySourceStatus.PARTIAL,
            nsdDiscoveryStatus(listOf(true), setOf("192.168.1.20"), true, true, false)
        )
        assertEquals(
            DiscoverySourceStatus.PARTIAL,
            nsdDiscoveryStatus(listOf(true), setOf("192.168.1.20"), true, false, true)
        )
    }

    @Test
    fun successfulEmptyWindowMeansNoResponses() {
        assertEquals(
            DiscoverySourceStatus.NO_RESPONSES,
            nsdDiscoveryStatus(listOf(true, true), emptySet(), false, false, false)
        )
    }

    @Test
    fun emptyServiceTypeListIsUnavailable() {
        assertEquals(
            DiscoverySourceStatus.UNAVAILABLE,
            nsdDiscoveryStatus(emptyList(), emptySet(), false, false, false)
        )
    }
}
