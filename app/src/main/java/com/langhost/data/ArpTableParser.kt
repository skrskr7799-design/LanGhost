package com.langhost.data

import com.langhost.data.model.DiscoverySourceStatus

/** Parses /proc/net/arp while retaining only entries owned by the active interface. */
internal fun parseArpTable(content: String, interfaceName: String): Map<String, String> {
    if (interfaceName.isBlank()) return emptyMap()
    return content.lineSequence()
        .drop(1) // Header: IP address, HW type, Flags, HW address, Mask, Device
        .mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 6 || parts[5] != interfaceName) return@mapNotNull null
            val ip = parts[0]
            val flags = parts[2].removePrefix("0x").toIntOrNull(16) ?: 0
            val mac = parts[3]
            // Only complete ARP entries (ATF_COM) are usable; pending/incomplete
            // neighbor entries may contain a MAC-shaped value but are not evidence.
            if (flags and 0x2 == 0 || !isUsableUnicastMac(mac)) {
                return@mapNotNull null
            }
            ip to mac.uppercase()
        }
        .toMap()
}


private fun isUsableUnicastMac(value: String): Boolean {
    if (!value.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))) return false
    val octets = value.split(":").map { it.toInt(16) }
    val isAllZero = octets.all { it == 0 }
    val isMulticast = octets.first() and 0x01 != 0
    return !isAllZero && !isMulticast
}

/** Distinguishes a readable empty ARP table from a failed or unavailable read. */
internal fun arpSourceStatus(available: Boolean, entries: Map<String, String>): DiscoverySourceStatus =
    when {
        !available -> DiscoverySourceStatus.UNAVAILABLE
        entries.isNotEmpty() -> DiscoverySourceStatus.RESPONDED
        else -> DiscoverySourceStatus.NO_RESPONSES
    }
