package com.langhost.data.network

import java.security.MessageDigest

object NetworkIdentity {
    fun scopeId(ssid: String, gateway: String, ipAddress: String, netmask: String): String {
        val parts = identityParts(ssid, gateway, ipAddress, netmask)
        if (parts.all { it.isBlank() }) return "unknown"
        val material = parts.joinToString(separator = "") { value -> "${value.length}:$value" }
        return digest(material)
    }

    /** Previous delimiter-based identity, used only to migrate saved device customizations. */
    fun legacyScopeId(ssid: String, gateway: String, ipAddress: String, netmask: String): String {
        val parts = identityParts(ssid, gateway, ipAddress, netmask)
        if (parts.all(String::isBlank)) return "unknown"
        return digest(parts.joinToString("|"))
    }

    private fun identityParts(ssid: String, gateway: String, ipAddress: String, netmask: String): List<String> {
        val normalizedSsid = ssid.trim().takeUnless { it.isBlank() || it == "—" || it == "<unknown ssid>" }.orEmpty()
        val normalizedGateway = gateway.trim().takeUnless { it.isBlank() || it == "0.0.0.0" || it == "—" }.orEmpty()
        val subnet = if (
            ipAddress.trim().isNotBlank() &&
            ipAddress.trim() != "0.0.0.0" &&
            netmask.trim().isNotBlank() &&
            netmask.trim() != "0.0.0.0"
        ) {
            runCatching {
                val parsed = Ipv4Subnet.fromAddressAndNetmask(ipAddress, netmask)
                "${parsed.networkAddressString()}/${parsed.prefixLength}"
            }.getOrDefault("")
        } else {
            ""
        }
        return listOf(normalizedSsid, normalizedGateway, subnet)
    }

    private fun digest(material: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
