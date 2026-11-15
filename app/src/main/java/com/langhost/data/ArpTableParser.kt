package com.langhost.data

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
            if (flags and 0x2 == 0 || mac == "00:00:00:00:00:00" ||
                !mac.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))) {
                return@mapNotNull null
            }
            ip to mac.uppercase()
        }
        .toMap()
}
