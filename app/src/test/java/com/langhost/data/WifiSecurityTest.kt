package com.langhost.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiSecurityTest {
    @Test fun classifiesOpenNetwork() {
        assertEquals("Open", WifiSecurity.classify("[ESS]"))
    }

    @Test fun classifiesWepBeforeGenericSecurityMarkers() {
        assertEquals("WEP", WifiSecurity.classify("[WEP][ESS]"))
    }

    @Test fun classifiesWpa2Psk() {
        assertEquals("WPA2", WifiSecurity.classify("[WPA2-PSK-CCMP][ESS]"))
    }

    @Test fun classifiesWpa3Sae() {
        assertEquals("WPA3", WifiSecurity.classify("[RSN-SAE-CCMP][ESS]"))
    }

    @Test fun identifiesTransitionModeWithoutOverstatingIt() {
        assertEquals("WPA2/WPA3", WifiSecurity.classify("[RSN-PSK+SAE-CCMP][ESS]"))
    }

    @Test fun classifiesOweAsEnhancedOpen() {
        assertEquals("OWE", WifiSecurity.classify("[OWE_TRANSITION][ESS]"))
    }

    @Test fun doesNotAssumeUnknownCapabilitiesAreOpen() {
        assertEquals("Unknown", WifiSecurity.classify(""))
        assertEquals("Unknown", WifiSecurity.classify("[UNKNOWN]"))
    }
}
