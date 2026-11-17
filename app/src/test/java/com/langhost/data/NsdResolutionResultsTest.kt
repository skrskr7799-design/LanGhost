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
        val printerGeneration = results.serviceObserved("printer|_ipp._tcp.", evidence("printer"))
        val nasGeneration = results.serviceObserved("nas|_smb._tcp.", evidence("nas"))
        results.resolved("printer|_ipp._tcp.", printerGeneration, setOf("192.168.1.20"))
        results.resolved("nas|_smb._tcp.", nasGeneration, setOf("192.168.1.30"))

        results.serviceLost("printer|_ipp._tcp.")

        assertEquals(setOf("192.168.1.30"), results.addresses())
        assertEquals(listOf(evidence("nas")), results.protocolEvidenceByIp()["192.168.1.30"])
    }

    @Test
    fun sharedAddressRemainsWhenAnotherServiceStillAdvertisesIt() {
        val results = NsdResolutionResults()
        val httpGeneration = results.serviceObserved("host|_http._tcp.", evidence("http"))
        val httpsGeneration = results.serviceObserved("host|_https._tcp.", evidence("https"))
        results.resolved("host|_http._tcp.", httpGeneration, setOf("192.168.1.20"))
        results.resolved("host|_https._tcp.", httpsGeneration, setOf("192.168.1.20"))

        results.serviceLost("host|_http._tcp.")

        assertEquals(setOf("192.168.1.20"), results.addresses())
        assertEquals(listOf(evidence("https")), results.protocolEvidenceByIp()["192.168.1.20"])
    }

    @Test
    fun lateResolutionFromLostAdvertisementCannotOverwriteReappearedService() {
        val results = NsdResolutionResults()
        val oldGeneration = results.serviceObserved("speaker|_http._tcp.", evidence("old"))
        results.serviceLost("speaker|_http._tcp.", oldGeneration)
        val newGeneration = results.serviceObserved("speaker|_http._tcp.", evidence("new"))

        results.resolved("speaker|_http._tcp.", oldGeneration, setOf("192.168.1.99"))

        assertTrue(results.addresses().isEmpty())
        assertEquals(emptyMap<String, List<ProtocolEvidence>>(), results.protocolEvidenceByIp())

        results.resolved("speaker|_http._tcp.", newGeneration, setOf("192.168.1.50"))
        assertEquals(setOf("192.168.1.50"), results.addresses())
        assertEquals(listOf(evidence("new")), results.protocolEvidenceByIp()["192.168.1.50"])
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
