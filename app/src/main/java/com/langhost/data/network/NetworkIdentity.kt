package com.langhost.data.network

import java.security.MessageDigest

object NetworkIdentity {
    fun scopeId(ssid: String, gateway: String, ipAddress: String, netmask: String): String {
        val normalizedSsid = ssid.trim().takeUnless { it.isBlank() || it == "—" || it == "<unknown ssid>" }.orEmpty()
        val normalizedGateway = gateway.trim().takeUnless { it.isBlank() || it == "0.0.0.0" || it == "—" }.orEmpty()
        val subnet = runCatching {
            val parsed = Ipv4Subnet.fromAddressAndNetmask(ipAddress, netmask)
            "${parsed.networkAddressString()}/${parsed.prefixLength}"
        }.getOrDefault("")
        // Length-prefix each field so arbitrary SSID characters cannot create ambiguous
        // concatenations (for example, an SSID containing the separator itself).
        val material = listOf(normalizedSsid, normalizedGateway, subnet)
            .joinToString(separator = "") { value -> "${value.length}:$value" }
        if (normalizedSsid.isBlank() && normalizedGateway.isBlank() && subnet.isBlank()) return "unknown"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
