package com.langhost.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NetworkIdentityTest {
    @Test
    fun accessPointChangesDoNotAffectLogicalNetworkScope() {
        val scopeA = NetworkIdentity.scopeId("Home WiFi", "192.168.1.1", "192.168.1.20", "255.255.255.0")
        val scopeB = NetworkIdentity.scopeId("Home WiFi", "192.168.1.1", "192.168.1.21", "255.255.255.0")
        assertEquals(scopeA, scopeB)
    }

    @Test
    fun differentGatewayOrSubnetProducesDifferentScope() {
        val base = NetworkIdentity.scopeId("Home WiFi", "192.168.1.1", "192.168.1.20", "255.255.255.0")
        val otherGateway = NetworkIdentity.scopeId("Home WiFi", "192.168.2.1", "192.168.2.20", "255.255.255.0")
        val otherSsid = NetworkIdentity.scopeId("Guest WiFi", "192.168.1.1", "192.168.1.20", "255.255.255.0")
        assertNotEquals(base, otherGateway)
        assertNotEquals(base, otherSsid)
    }

    @Test
    fun scopeDoesNotExposeSsidInIdentifier() {
        val scope = NetworkIdentity.scopeId("Private SSID", "192.168.1.1", "192.168.1.20", "255.255.255.0")
        assertEquals(16, scope.length)
        assertEquals(false, scope.contains("Private"))
    }
    @Test
    fun scopeEncodingDoesNotCollideWhenSsidContainsSeparatorCharacters() {
        val first = NetworkIdentity.scopeId(
            "home|office", "192.168.1.1", "192.168.1.20", "255.255.255.0"
        )
        val second = NetworkIdentity.scopeId(
            "home", "office|192.168.1.1", "192.168.1.20", "255.255.255.0"
        )

        assertNotEquals(first, second)
    }

    @Test
    fun missingNetworkIdentifiersRemainUnknown() {
        assertEquals("unknown", NetworkIdentity.scopeId("—", "0.0.0.0", "0.0.0.0", "0.0.0.0"))
    }

}
