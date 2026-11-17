package com.langhost.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NsdResolutionResultsTest {
    @Test
    fun serviceLostRemovesOnlyItsOwnResolvedAddresses() {
        val results = NsdResolutionResults()
        results.resolved("printer|_ipp._tcp.", setOf("192.168.1.20"))
        results.resolved("nas|_smb._tcp.", setOf("192.168.1.30"))

        results.serviceLost("printer|_ipp._tcp.")

        assertEquals(setOf("192.168.1.30"), results.addresses())
    }

    @Test
    fun sharedAddressRemainsWhenAnotherServiceStillAdvertisesIt() {
        val results = NsdResolutionResults()
        results.resolved("host|_http._tcp.", setOf("192.168.1.20"))
        results.resolved("host|_https._tcp.", setOf("192.168.1.20"))

        results.serviceLost("host|_http._tcp.")

        assertEquals(setOf("192.168.1.20"), results.addresses())
    }

    @Test
    fun resolutionFailureIsTrackedSeparatelyFromNoServiceResponses() {
        val results = NsdResolutionResults()

        assertFalse(results.hasObservedServices())
        assertFalse(results.hasResolutionFailures())

        results.serviceObserved()
        results.resolutionFailed()

        assertTrue(results.hasObservedServices())
        assertTrue(results.hasResolutionFailures())
        assertEquals(emptySet<String>(), results.addresses())
    }
}
