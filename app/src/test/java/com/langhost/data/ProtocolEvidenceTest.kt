package com.langhost.data

import com.langhost.data.model.ProtocolEvidence
import com.langhost.data.model.ProtocolEvidenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProtocolEvidenceTest {
    @Test
    fun sanitizerRemovesControlCharactersAndTrimsLength() {
        val sanitized = ProtocolEvidence.sanitize("  Living\u0000 Room\nSpeaker  ", 11)

        assertEquals("Living Room", sanitized)
        assertFalse(sanitized.any { it.code < 0x20 || it.code == 0x7f })
    }

    @Test
    fun sanitizerRemovesUnicodeControlCharactersButPreservesReadableText() {
        val sanitized = ProtocolEvidence.sanitize("Кухня\u0085 TV\u009F")

        assertEquals("Кухня TV", sanitized)
        assertFalse(sanitized.any { Character.isISOControl(it) })
    }

    @Test
    fun evidenceDefaultsRemainSafeAndSourceIsExplicit() {
        val evidence = ProtocolEvidence(source = ProtocolEvidenceSource.DNS_SD)

        assertEquals("", evidence.serviceName)
        assertEquals("", evidence.server)
        assertEquals(ProtocolEvidenceSource.DNS_SD, evidence.source)
    }
}
