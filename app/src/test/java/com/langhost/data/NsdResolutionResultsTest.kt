package com.langhost.data

import com.langhost.data.model.ProtocolEvidence
import com.langhost.data.model.ProtocolEvidenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NsdResolutionResultsTest {
    private fun evidence(name: String) = ProtocolEvidence(
        source = ProtocolEvidenceSource.DNS_SD,
        serviceType = "_http._tcp.",
        serviceName = name
    )

    @Test
    fun serviceLostRemovesOnlyItsOwnResolvedAddressesAndMetadata() {
        val results = NsdResolutionResults()
        results.serviceObserved("printer|_ipp._tcp.", evidence("printer"))
        results.serviceObserved("nas|_smb._tcp.", evidence("nas"))
        results.resolved("printer|_ipp._tcp.", setOf("192.168.1.20"))
        results.resolved("nas|_smb._tcp.", setOf("192.168.1.30"))

        results.serviceLost("printer|_ipp._tcp.")

        assertEquals(setOf("192.168.1.30"), results.addresses())
        assertEquals(listOf(evidence("nas")), results.protocolEvidenceByIp()["192.168.1.30"])
    }

    @Test
    fun sharedAddressRemainsWhenAnotherServiceStillAdvertisesIt() {
        val results = NsdResolutionResults()
        results.serviceObserved("host|_http._tcp.", evidence("http"))
        results.serviceObserved("host|_https._tcp.", evidence("https"))
        results.resolved("host|_http._tcp.", setOf("192.168.1.20"))
        results.resolved("host|_https._tcp.", setOf("192.168.1.20"))

        results.serviceLost("host|_http._tcp.")

        assertEquals(setOf("192.168.1.20"), results.addresses())
        assertEquals(listOf(evidence("https")), results.protocolEvidenceByIp()["192.168.1.20"])
    }

    @Test
    fun resolutionFailureIsTrackedSeparatelyFromNoServiceResponses() {
        val results = NsdResolutionResults()

        assertFalse(results.hasObservedServices())
        assertFalse(results.hasResolutionFailures())

        results.serviceObserved("failed|_http._tcp.", evidence("failed"))
        results.resolutionFailed()

        assertTrue(results.hasObservedServices())
        assertTrue(results.hasResolutionFailures())
        assertEquals(emptySet<String>(), results.addresses())
        assertTrue(results.protocolEvidenceByIp().isEmpty())
    }
}
