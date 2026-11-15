package com.langhost.data.network

/**
 * An IPv4 subnet derived from a host address and a contiguous IPv4 netmask.
 *
 * All arithmetic is performed in a Long so the full unsigned IPv4 range
 * (0..4,294,967,295) is represented without signed Int overflow.
 */
class Ipv4Subnet private constructor(
    val address: Long,
    val prefixLength: Int
) {
    private val hostMask: Long = if (prefixLength == 0) IPV4_MAX else
        (1L shl (32 - prefixLength)) - 1L

    val networkAddress: Long = address and hostMask.inv() and IPV4_MAX
    val broadcastAddress: Long = networkAddress or hostMask

    /** Number of addresses usable as endpoints. /31 follows RFC 3021; /32 has one address. */
    val usableHostCount: Long
        get() = lastUsableAddress - firstUsableAddress + 1L

    private val firstUsableAddress: Long
        get() = if (prefixLength <= 30) networkAddress + 1L else networkAddress

    private val lastUsableAddress: Long
        get() = if (prefixLength <= 30) broadcastAddress - 1L else broadcastAddress

    fun contains(ipAddress: String): Boolean {
        val parsed = parseAddress(ipAddress) ?: return false
        return parsed in networkAddress..broadcastAddress
    }

    fun isUsableHost(ipAddress: String): Boolean {
        val parsed = parseAddress(ipAddress) ?: return false
        return parsed in firstUsableAddress..lastUsableAddress
    }

    /**
     * Returns at most [limit] usable addresses, expanding outwards from [prioritizeAddress].
     * This avoids silently assuming that a subnet is /24 and prevents huge subnets
     * from allocating an unbounded candidate list.
     */
    fun hostAddresses(limit: Int, prioritizeAddress: String = formatAddress(address)): List<String> {
        require(limit >= 0) { "limit must not be negative" }
        if (limit == 0 || usableHostCount == 0L) return emptyList()

        val priority = parseAddress(prioritizeAddress)
            ?.takeIf { it in firstUsableAddress..lastUsableAddress }
            ?: address.coerceIn(firstUsableAddress, lastUsableAddress)

        val result = ArrayList<String>(minOf(limit.toLong(), usableHostCount).toInt())
        result += formatAddress(priority)

        var distance = 1L
        while (result.size < limit) {
            val lower = priority - distance
            val upper = priority + distance
            var added = false

            if (lower >= firstUsableAddress) {
                result += formatAddress(lower)
                added = true
                if (result.size == limit) break
            }
            if (upper <= lastUsableAddress && result.size < limit) {
                result += formatAddress(upper)
                added = true
            }
            if (!added && lower < firstUsableAddress && upper > lastUsableAddress) break
            distance++
        }
        return result
    }

    fun networkAddressString(): String = formatAddress(networkAddress)
    fun broadcastAddressString(): String = formatAddress(broadcastAddress)

    companion object {
        private const val IPV4_MAX = 0xFFFF_FFFFL

        fun fromAddressAndNetmask(address: String, netmask: String): Ipv4Subnet {
            val parsedAddress = parseAddress(address)
                ?: throw IllegalArgumentException("Invalid IPv4 address: $address")
            val parsedMask = parseAddress(netmask)
                ?: throw IllegalArgumentException("Invalid IPv4 netmask: $netmask")

            val invertedMask = parsedMask.inv() and IPV4_MAX
            require((invertedMask and (invertedMask + 1L)) == 0L) {
                "Netmask is not contiguous: $netmask"
            }

            val prefixLength = java.lang.Long.bitCount(parsedMask).toInt()
            return Ipv4Subnet(parsedAddress, prefixLength)
        }

        fun parseAddress(value: String): Long? {
            val parts = value.split('.')
            if (parts.size != 4) return null

            var result = 0L
            for (part in parts) {
                if (part.isEmpty() || part.any { !it.isDigit() }) return null
                val octet = part.toIntOrNull() ?: return null
                if (octet !in 0..255) return null
                result = (result shl 8) or octet.toLong()
            }
            return result
        }

        fun formatAddress(value: Long): String {
            require(value in 0L..IPV4_MAX) { "IPv4 value is out of range" }
            return listOf(
                (value shr 24) and 0xFF,
                (value shr 16) and 0xFF,
                (value shr 8) and 0xFF,
                value and 0xFF
            ).joinToString(".")
        }
    }
}
