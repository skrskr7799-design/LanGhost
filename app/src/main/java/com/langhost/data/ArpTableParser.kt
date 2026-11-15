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
            val mac = parts[3]
            if (mac == "00:00:00:00:00:00" || !mac.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))) {
                return@mapNotNull null
            }
            ip to mac.uppercase()
        }
        .toMap()
}
