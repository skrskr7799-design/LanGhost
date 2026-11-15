package com.langhost.data.model

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
    val osGuess: String = "",
    val nickname: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val color: Long? = null,
    val isFavorite: Boolean = false,
    val isWatched: Boolean = false,
    /** Network BSSID when discovered — used for stable identity */
    val networkBssid: String = ""
) {
    val displayName: String
        get() = when {
            nickname.isNotBlank() -> nickname
            hostname.isNotBlank() && hostname != ip -> hostname
            vendor.isNotBlank() -> vendor
            else -> ip
        }

    val isOnline: Boolean
        get() = (System.currentTimeMillis() - lastSeen) < 10 * 60 * 1000 // 10 min

    /** Stable key: MAC@BSSID, fallback MAC, fallback IP */
    val stableId: String
        get() = when {
            mac.isNotBlank() && networkBssid.isNotBlank() && networkBssid != "—" ->
                "${mac.uppercase()}@${networkBssid.uppercase()}"
            mac.isNotBlank() -> mac.uppercase()
            else -> "ip:$ip"
        }

    fun withUpdatedPorts(ports: List<Int>): Device {
        return copy(
            openPorts = ports,
            osGuess = guessOs(ports, vendor, hostname)
        )
    }

    companion object {
        fun guessOs(ports: List<Int>, vendor: String, hostname: String): String {
            val p = ports.toSet()
            val v = vendor.lowercase()
            val h = hostname.lowercase()

            return when {
                v.contains("apple") || h.contains("iphone") || h.contains("ipad") || h.contains("macbook") -> "Apple (iOS/macOS)"
                v.contains("samsung") -> "Android / Samsung"
                v.contains("xiaomi") || v.contains("huawei") || v.contains("redmi") -> "Android"
                v.contains("microsoft") || v.contains("surface") -> "Windows"
                v.contains("raspberry") -> "Linux (Raspberry Pi)"
                v.contains("cisco") || v.contains("tp-link") || v.contains("asus") || v.contains("d-link") || v.contains("linksys") -> "Router / Network Device"
                v.contains("google") -> "Android / Google"
                3389 in p -> "Windows (RDP)"
                22 in p && 445 !in p && 139 !in p -> "Linux / Unix (SSH)"
                445 in p || 139 in p -> "Windows / SMB Device"
                548 in p || 5900 in p -> "macOS / VNC"
                80 in p || 443 in p || 8080 in p -> "Web Server / IoT"
                9100 in p -> "Printer"
                53 in p -> "DNS / Router"
                ports.isNotEmpty() -> "Unknown (has open ports)"
                else -> "Unknown"
            }
        }
    }
}
