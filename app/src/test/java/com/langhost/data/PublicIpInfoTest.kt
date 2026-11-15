package com.langhost.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class PublicIpInfoTest {
    @Test
    fun responseReaderAcceptsPayloadAtLimit() {
        val payload = "12345678"
        assertEquals(payload, PublicIpLookup.readLimitedText(ByteArrayInputStream(payload.toByteArray()), 8))
    }

    @Test(expected = IllegalStateException::class)
    fun responseReaderRejectsPayloadOverLimit() {
        PublicIpLookup.readLimitedText(ByteArrayInputStream("123456789".toByteArray()), 8)
    }

    @Test
    fun validatesIpLiteralsWithoutAcceptingHostnames() {
        assertEquals(true, PublicIpLookup.isIpLiteral("203.0.113.8"))
        assertEquals(true, PublicIpLookup.isIpLiteral("2001:db8::1"))
        assertEquals(false, PublicIpLookup.isIpLiteral("ipapi.co"))
        assertEquals(false, PublicIpLookup.isIpLiteral("203.0.113.999"))
        assertEquals(false, PublicIpLookup.isIpLiteral("203.000.113.8"))
    }
}
