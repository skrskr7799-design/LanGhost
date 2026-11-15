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
}
