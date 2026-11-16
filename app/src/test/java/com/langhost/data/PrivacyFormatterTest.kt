package com.langhost.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyFormatterTest {
    @Test
    fun masksIpv4WhilePreservingLocalPrefix() {
        assertEquals("192.168.••.••", PrivacyFormatter.ip("192.168.1.42", true))
    }

    @Test
    fun masksIpv6WithoutChangingRawValueWhenDisabled() {
        assertEquals("2001:db8:••••:••••", PrivacyFormatter.ip("2001:db8:abcd:1234", true))
        assertEquals("2001:db8:abcd:1234", PrivacyFormatter.ip("2001:db8:abcd:1234", false))
    }

    @Test
    fun masksMacAndBssidButKeepsLastOctetForRecognition() {
        assertEquals("••:••:••:••:••:FF", PrivacyFormatter.mac("AA:BB:CC:DD:EE:FF", true))
        assertEquals("••-••-••-••-••-FF", PrivacyFormatter.mac("AA-BB-CC-DD-EE-FF", true))
        assertEquals("AA:BB:CC:DD:EE:FF", PrivacyFormatter.bssid("AA:BB:CC:DD:EE:FF", false))
    }

    @Test
    fun leavesUnavailableValuesAndDisabledMaskingUntouched() {
        assertEquals("—", PrivacyFormatter.ip("—", true))
        assertEquals("", PrivacyFormatter.mac("", true))
        assertEquals("10.0.0.8", PrivacyFormatter.ip("10.0.0.8", false))
    }
}
