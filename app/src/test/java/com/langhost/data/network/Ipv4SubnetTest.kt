package com.langhost.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4SubnetTest {

    @Test
    fun calculatesStandard24Subnet() {
        val subnet = Ipv4Subnet.fromAddressAndNetmask("192.168.1.42", "255.255.255.0")

        assertEquals(24, subnet.prefixLength)
        assertEquals("192.168.1.0", subnet.networkAddressString())
        assertEquals("192.168.1.255", subnet.broadcastAddressString())
        assertEquals(254L, subnet.usableHostCount)
        assertTrue(subnet.isUsableHost("192.168.1.42"))
        assertFalse(subnet.isUsableHost("192.168.1.255"))
        assertFalse(subnet.contains("192.168.2.1"))
    }

    @Test
    fun correctlyEnumeratesAcrossOctetBoundaryFor23Subnet() {
        val subnet = Ipv4Subnet.fromAddressAndNetmask("192.168.1.42", "255.255.254.0")

        assertEquals("192.168.0.0", subnet.networkAddressString())
        assertEquals("192.168.1.255", subnet.broadcastAddressString())
        assertTrue(subnet.isUsableHost("192.168.0.10"))
        assertTrue(subnet.isUsableHost("192.168.1.254"))
        assertEquals(
            listOf("192.168.1.42", "192.168.1.41", "192.168.1.43", "192.168.1.40"),
            subnet.hostAddresses(4, "192.168.1.42")
        )
    }

    @Test
    fun supportsLargerSubnetsWithoutReturningUnboundedLists() {
        val subnet = Ipv4Subnet.fromAddressAndNetmask("10.12.34.56", "255.255.0.0")

        val hosts = subnet.hostAddresses(5, "10.12.34.56")
        assertEquals(5, hosts.size)
        assertEquals("10.12.34.56", hosts.first())
        assertTrue(hosts.all(subnet::isUsableHost))
    }

    @Test
    fun handlesPointToPointAndSingleAddressPrefixes() {
        val pointToPoint = Ipv4Subnet.fromAddressAndNetmask("192.0.2.10", "255.255.255.254")
        val single = Ipv4Subnet.fromAddressAndNetmask("192.0.2.10", "255.255.255.255")

        assertEquals(2L, pointToPoint.usableHostCount)
        assertTrue(pointToPoint.isUsableHost("192.0.2.10"))
        assertTrue(pointToPoint.isUsableHost("192.0.2.11"))
        assertEquals(1L, single.usableHostCount)
        assertEquals(listOf("192.0.2.10"), single.hostAddresses(10))
    }

    @Test
    fun rejectsInvalidAddressesAndNonContiguousMasks() {
        assertEquals(null, Ipv4Subnet.parseAddress("192.168.1.256"))
        assertEquals(null, Ipv4Subnet.parseAddress("192.168.1"))
        try {
            Ipv4Subnet.fromAddressAndNetmask("192.168.1.10", "255.0.255.0")
            throw AssertionError("Expected invalid netmask to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun handlesZeroPrefixAndUnsignedHighAddresses() {
        val subnet = Ipv4Subnet.fromAddressAndNetmask("255.255.255.254", "0.0.0.0")

        assertEquals(0, subnet.prefixLength)
        assertEquals("0.0.0.0", subnet.networkAddressString())
        assertEquals("255.255.255.255", subnet.broadcastAddressString())
        assertEquals("255.255.255.254", subnet.hostAddresses(1).first())
    }
}
