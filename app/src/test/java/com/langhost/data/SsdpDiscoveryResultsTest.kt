package com.langhost.data

import com.langhost.data.model.DiscoverySourceStatus
import com.langhost.data.model.ProtocolEvidence
import com.langhost.data.model.ProtocolEvidenceSource
import org.junit.Assert.assertEquals
import org.junit.Test

class SsdpDiscoveryResultsTest {
    private val evidence = ProtocolEvidence(
        source = ProtocolEvidenceSource.SSDP,
        deviceType = "urn:schemas-upnp-org:device:MediaRenderer:1",
        server = "Linux/1.0 UPnP/1.1 Example/2.0"
    )

    @Test
    fun successfulResponsesProduceRespondedAndRetainMetadata() {
        val results = SsdpDiscoveryResults()
        results.addResponder("192.168.1.1", evidence)

        assertEquals(setOf("192.168.1.1"), results.snapshot())
        assertEquals(listOf(evidence), results.protocolEvidenceByIp()["192.168.1.1"])
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
        results.addResponder("192.168.1.1", evidence)
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
