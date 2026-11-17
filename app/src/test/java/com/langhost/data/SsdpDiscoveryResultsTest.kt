package com.langhost.data

import com.langhost.data.model.DiscoverySourceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SsdpDiscoveryResultsTest {
    @Test
    fun successfulResponsesProduceResponded() {
        val results = SsdpDiscoveryResults()
        results.addResponder("192.168.1.1")

        assertEquals(setOf("192.168.1.1"), results.snapshot())
        assertEquals(DiscoverySourceStatus.RESPONDED, results.status())
    }

    @Test
    fun noResponsesAfterSuccessfulWindowProduceNoResponses() {
        val results = SsdpDiscoveryResults()

        assertEquals(DiscoverySourceStatus.NO_RESPONSES, results.status())
    }

    @Test
    fun failureAfterAValidResponsePreservesEvidenceAsPartial() {
        val results = SsdpDiscoveryResults()
        results.addResponder("192.168.1.1")
        results.markOperationFailed()

        assertEquals(setOf("192.168.1.1"), results.snapshot())
        assertEquals(DiscoverySourceStatus.PARTIAL, results.status())
    }

    @Test
    fun failureWithoutResponsesProducesUnavailable() {
        val results = SsdpDiscoveryResults()
        results.markOperationFailed()

        assertEquals(emptySet<String>(), results.snapshot())
        assertEquals(DiscoverySourceStatus.UNAVAILABLE, results.status())
    }
}
