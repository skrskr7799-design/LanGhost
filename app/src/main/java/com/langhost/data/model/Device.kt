package com.langhost.data.model

enum class DiscoveryEvidence {
    TCP_RESPONSE,
    NSD_SERVICE_OBSERVATION,
    SSDP_RESPONSE_OBSERVATION,
    HOST_REACHABILITY_PROBE,
    ARP_OBSERVATION,
    LOCAL_INTERFACE,
    GATEWAY_CONFIGURATION,
    UNKNOWN
}

enum class DeviceType {
    PHONE, TABLET, TV, PC, LAPTOP, ROUTER, PRINTER, NAS, CAMERA, IOT, SERVER, UNKNOWN
}

data class Device(
    val ip: String,
    val mac: String = "",
    val hostname: String = "",
    val vendor: String = "",
    val isLocalDevice: Boolean = false,
    val isGateway: Boolean = false,
    val firstSeen: Long = System.currentTimeMillis(),
    /** Most recent observation of this IP/device, including passive ARP-table evidence. */
    val lastSeen: Long = System.currentTimeMillis(),
    /** Most recent positive reachability confirmation; zero means never confirmed. */
    val lastConfirmedOnline: Long = lastSeen,
    /** Source of the latest discovery observation; ARP presence alone is not a reachability proof. */
    val discoveryEvidence: DiscoveryEvidence = DiscoveryEvidence.TCP_RESPONSE,
    val openPorts: List<Int> = emptyList(),
    /** Ports confirmed by the dedicated security-service scan, kept separate from general discovery. */
    val auditedPorts: List<Int> = emptyList(),
    /** Per-port outcomes from the dedicated security audit; unlike openPorts, includes uncertainty. */
    val auditedPortResults: List<PortAuditResult> = emptyList(),
    val osGuess: String = "",
    val nickname: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val color: Long? = null,
    val isFavorite: Boolean = false,
    val isWatched: Boolean = false,
    val networkBssid: String = "",
    val networkScope: String = "",
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    /** 0 means the type is unknown; otherwise a conservative heuristic confidence from 45 to 100. */
    val typeConfidence: Int = 0,
    /** Stable, non-sensitive signal identifiers that explain the current type estimate. */
    val typeEvidence: List<String> = emptyList(),
    val httpFingerprint: String = "",
    /** Sanitized DNS-SD/SSDP metadata; never stores raw packets or full LOCATION URLs. */
    val protocolEvidence: List<ProtocolEvidence> = emptyList(),
    val isRandomMac: Boolean = false,
    /** Timestamp of the most recent completed general TCP port scan; zero means never scanned. */
    val portsScannedAt: Long = 0L
) {
    val displayName: String
        get() = when {
            nickname.isNotBlank() -> nickname
            hostname.isNotBlank() && hostname != ip -> hostname
            vendor.isNotBlank() && vendor != "Random MAC" -> vendor
            else -> ip
        }

    val isOnline: Boolean
        get() = lastConfirmedOnline > 0L &&
            System.currentTimeMillis() >= lastConfirmedOnline &&
            (System.currentTimeMillis() - lastConfirmedOnline) < 10 * 60 * 1000L

    val stableId: String
        get() = when {
            mac.isNotBlank() && networkScope.isNotBlank() ->
                "${mac.uppercase()}@${networkScope}"
            mac.isNotBlank() -> mac.uppercase()
            networkScope.isNotBlank() -> "ip:${networkScope}:$ip"
            else -> "ip:$ip"
        }

    /** True only when two snapshots describe the same device endpoint on the same network. */
    fun hasSameEndpointAs(other: Device): Boolean =
        stableId == other.stableId && networkScope == other.networkScope && ip == other.ip

    fun withUpdatedPorts(ports: List<Int>): Device {
        val os = guessOs(ports, vendor, hostname)
        val classification = DeviceTypeClassifier.classify(hostname, vendor, ports, isGateway, httpFingerprint)
        return copy(
            openPorts = ports,
            osGuess = os,
            deviceType = classification.type,
            typeConfidence = classification.confidence,
            typeEvidence = classification.signals,
            portsScannedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun isRandomMacAddress(mac: String): Boolean {
            if (mac.length < 2) return false
            val firstOctet = mac.take(2).toIntOrNull(16) ?: return false
            // IEEE 802 MAC U/L bit (bit 1 of the first octet) marks locally administered addresses.
            return (firstOctet and 0x02) != 0
        }

        fun guessOs(ports: List<Int>, vendor: String, hostname: String): String {
            val p = ports.toSet()
            val v = vendor.lowercase()
            val h = hostname.lowercase()
            return when {
                v.contains("apple") || h.contains("iphone") || h.contains("ipad") || h.contains("macbook") -> "Apple (iOS/macOS)"
                v.contains("samsung") && (h.contains("tv") || 8008 in p || 8009 in p) -> "Tizen / Samsung TV"
                v.contains("samsung") -> "Android / Samsung"
                v.contains("xiaomi") || v.contains("huawei") || v.contains("redmi") -> "Android"
                v.contains("microsoft") || v.contains("surface") -> "Windows"
                v.contains("raspberry") -> "Linux (Raspberry Pi)"
                v.contains("cisco") || v.contains("tp-link") || v.contains("asus") || v.contains("d-link") || v.contains("linksys") -> "Router / Network Device"
                v.contains("google") || h.contains("chromecast") || h.contains("google") -> "Google / Cast"
                3389 in p -> "Windows (RDP)"
                22 in p && 445 !in p && 139 !in p -> "Linux / Unix (SSH)"
                445 in p || 139 in p -> "Windows / SMB Device"
                548 in p || 5900 in p -> "macOS / VNC"
                9100 in p -> "Printer"
                53 in p -> "DNS / Router"
                ports.isNotEmpty() -> "Unknown (has open ports)"
                else -> "Unknown"
            }
        }

        fun guessType(ports: List<Int>, vendor: String, hostname: String, isGateway: Boolean): DeviceType =
            DeviceTypeClassifier.classify(hostname, vendor, ports, isGateway).type
    }
}
