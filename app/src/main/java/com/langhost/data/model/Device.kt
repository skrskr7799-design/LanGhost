package com.langhost.data.model

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
    val lastSeen: Long = System.currentTimeMillis(),
    val openPorts: List<Int> = emptyList(),
    /** Ports confirmed by the dedicated security-service scan, kept separate from general discovery. */
    val auditedPorts: List<Int> = emptyList(),
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
    val httpFingerprint: String = "",
    val isRandomMac: Boolean = false
) {
    val displayName: String
        get() = when {
            nickname.isNotBlank() -> nickname
            hostname.isNotBlank() && hostname != ip -> hostname
            vendor.isNotBlank() && vendor != "Random MAC" -> vendor
            else -> ip
        }

    val isOnline: Boolean
        get() = (System.currentTimeMillis() - lastSeen) < 10 * 60 * 1000

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
        val type = guessType(ports, vendor, hostname, isGateway)
        return copy(openPorts = ports, osGuess = os, deviceType = type)
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

        fun guessType(ports: List<Int>, vendor: String, hostname: String, isGateway: Boolean): DeviceType {
            if (isGateway) return DeviceType.ROUTER
            val p = ports.toSet()
            val v = vendor.lowercase()
            val h = hostname.lowercase()
            return when {
                h.contains("iphone") || h.contains("android") || h.contains("phone") || h.contains("pixel") -> DeviceType.PHONE
                h.contains("ipad") || h.contains("tablet") -> DeviceType.TABLET
                h.contains("tv") || h.contains("chromecast") || h.contains("firetv") || h.contains("roku") ||
                    8008 in p || 8009 in p || 7000 in p -> DeviceType.TV
                h.contains("printer") || h.contains("print") || 9100 in p || 631 in p -> DeviceType.PRINTER
                h.contains("nas") || h.contains("synology") || h.contains("qnap") || 548 in p -> DeviceType.NAS
                h.contains("cam") || h.contains("camera") || h.contains("nvr") -> DeviceType.CAMERA
                v.contains("raspberry") || 22 in p && 80 in p -> DeviceType.SERVER
                3389 in p || 445 in p || h.contains("desktop") || h.contains("pc") || h.contains("laptop") ->
                    if (h.contains("laptop") || h.contains("macbook") || h.contains("notebook")) DeviceType.LAPTOP else DeviceType.PC
                v.contains("tp-link") || v.contains("asus") || v.contains("cisco") || v.contains("d-link") -> DeviceType.ROUTER
                h.contains("esp") || h.contains("tuya") || h.contains("smart") || h.contains("iot") -> DeviceType.IOT
                else -> DeviceType.UNKNOWN
            }
        }
    }
}
