package com.langhost.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException

class AndroidSsdpDiscoveryTest {
    @Test
    fun acceptsValidUpnpDiscoveryResponse() {
        val response = (
            "HTTP/1.1 200 OK\r\n" +
                "CACHE-CONTROL: max-age=120\r\n" +
                "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
                "USN: uuid:device-123::urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
            ).toByteArray(Charsets.US_ASCII)

        assertTrue(SsdpResponseParser.isValidResponse(response, response.size))
    }

    @Test
    fun acceptsValidResponseWithLocationInsteadOfUsnAndCaseInsensitiveHeaders() {
        val response = (
            "HTTP/1.1 200 OK\r\n" +
                "sT: upnp:rootdevice\r\n" +
                "lOcAtIoN: http://192.168.1.1:1900/device.xml\r\n\r\n"
            ).toByteArray(Charsets.US_ASCII)

        assertTrue(SsdpResponseParser.isValidResponse(response, response.size))
    }

    @Test
    fun rejectsInvalidLengthAndResponsesMissingIdentityHeaders() {
        val validStatusOnly = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=10\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
        val validStatusAndStOnly = "HTTP/1.1 200 OK\r\nST: ssdp:all\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
        val response = "HTTP/1.1 200 OK\r\nST: ssdp:all\r\nUSN: uuid:x\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)

        assertFalse(SsdpResponseParser.isValidResponse(response, 0))
        assertFalse(SsdpResponseParser.isValidResponse(response, response.size + 1))
        assertFalse(SsdpResponseParser.isValidResponse(validStatusOnly, validStatusOnly.size))
        assertFalse(SsdpResponseParser.isValidResponse(validStatusAndStOnly, validStatusAndStOnly.size))
    }

    @Test
    fun rejectsNonSuccessAndMalformedResponses() {
        val notFound = "HTTP/1.1 404 Not Found\r\nST: ssdp:all\r\nUSN: uuid:x\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
        val invalidStatus = "HTTP/1.1 2000 OK\r\nST: ssdp:all\r\nUSN: uuid:x\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
        val missingHeaders = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=10\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)

        assertFalse(SsdpResponseParser.isValidResponse(notFound, notFound.size))
        assertFalse(SsdpResponseParser.isValidResponse(invalidStatus, invalidStatus.size))
        assertFalse(SsdpResponseParser.isValidResponse(missingHeaders, missingHeaders.size))
    }

    private val responder = InetAddress.getByName("192.168.1.44")
    private val validPacket = (
        "HTTP/1.1 200 OK\\r\\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\\r\\n" +
            "USN: uuid:living-room-speaker\\r\\n\\r\\n"
        ).toByteArray(Charsets.US_ASCII)

    @Test
    fun receiveLoopKeepsResponderWhenSocketFailsAfterAValidReply() = runBlocking {
        val results = SsdpDiscoveryResults()
        var calls = 0

        runSsdpReceiveLoop(
            results = results,
            deadlineNanos = 10,
            nowNanos = { 0L },
            receive = {
                calls++
                if (calls == 1) SsdpReceivedDatagram(responder, validPacket, validPacket.size)
                else throw IOException("simulated socket failure")
            }
        )

        assertEquals(setOf("192.168.1.44"), results.snapshot())
        assertEquals(DiscoverySourceStatus.PARTIAL, results.status())
        assertEquals(1, results.protocolEvidenceByIp().getValue("192.168.1.44").size)
    }

    @Test
    fun receiveLoopReportsUnavailableWhenSocketFailsBeforeAnyReply() = runBlocking {
        val results = SsdpDiscoveryResults()

        runSsdpReceiveLoop(
            results = results,
            deadlineNanos = 10,
            nowNanos = { 0L },
            receive = { throw IOException("simulated socket failure") }
        )

        assertTrue(results.snapshot().isEmpty())
        assertEquals(DiscoverySourceStatus.UNAVAILABLE, results.status())
    }

    @Test
    fun receiveLoopContinuesAfterExpectedTimeoutAndIgnoresMalformedPackets() = runBlocking {
        val results = SsdpDiscoveryResults()
        var clockReads = 0
        var receives = 0

        runSsdpReceiveLoop(
            results = results,
            deadlineNanos = 10,
            nowNanos = {
                clockReads++
                if (clockReads < 4) 0L else 10L
            },
            receive = {
                receives++
                when (receives) {
                    1 -> throw SocketTimeoutException("expected receive timeout")
                    2 -> SsdpReceivedDatagram(responder, byteArrayOf(1, 2, 3), 3)
                    else -> SsdpReceivedDatagram(responder, validPacket, validPacket.size)
                }
            }
        )

        assertEquals(3, receives)
        assertEquals(setOf("192.168.1.44"), results.snapshot())
        assertEquals(DiscoverySourceStatus.RESPONDED, results.status())
    }

}
