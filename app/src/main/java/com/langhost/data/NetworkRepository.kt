package com.langhost.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.langhost.data.model.Device
import com.langhost.data.model.NetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class NetworkRepository(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // In-memory cache of discovered devices
    private val devicesCache = ConcurrentHashMap<String, Device>()

    @SuppressLint("MissingPermission", "HardwareIds")
    fun getCurrentNetworkInfo(): NetworkInfo {
        val wifiInfo = wifiManager.connectionInfo
        val dhcpInfo = wifiManager.dhcpInfo

        val ssid = wifiInfo.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" } ?: "—"
        val bssid = wifiInfo.bssid ?: "—"
        val signalDbm = wifiInfo.rssi
        val frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) wifiInfo.frequency else 0
        val linkSpeed = wifiInfo.linkSpeed

        val channel = frequencyToChannel(frequency)

        val ip = intToIp(wifiInfo.ipAddress)
        val gateway = intToIp(dhcpInfo.gateway)
        val netmask = intToIp(dhcpInfo.netmask)
        val dns1 = intToIp(dhcpInfo.dns1)
        val dns2 = intToIp(dhcpInfo.dns2)

        // Try to get better security info on newer APIs
        val security = getSecurityType()

        return NetworkInfo(
            ssid = ssid,
            bssid = bssid,
            signalDbm = signalDbm,
            frequencyMhz = frequency,
            linkSpeedMbps = linkSpeed,
            channel = channel,
            security = security,
            ipAddress = ip,
            gateway = gateway,
            netmask = netmask,
            dns1 = dns1,
            dns2 = dns2,
            networkId = wifiInfo.networkId,
            isConnected = wifiInfo.networkId != -1
        )
    }

    private fun getSecurityType(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Limited info without special permissions
                "WPA/WPA2/WPA3"
            } else {
                "WPA/WPA2"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun frequencyToChannel(freq: Int): Int {
        return when {
            freq in 2412..2484 -> (freq - 2412) / 5 + 1
            freq in 5170..5825 -> (freq - 5170) / 5 + 34
            else -> 0
        }
    }

    private fun intToIp(value: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            value and 0xff,
            value shr 8 and 0xff,
            value shr 16 and 0xff,
            value shr 24 and 0xff
        )
    }

    /**
     * Basic device discovery:
     * 1. Read ARP table
     * 2. Ping sweep of local subnet
     * 3. Merge results
     */
    suspend fun scanDevices(): List<Device> = withContext(Dispatchers.IO) {
        val networkInfo = getCurrentNetworkInfo()
        if (!networkInfo.isConnected || networkInfo.ipAddress == "—") {
            return@withContext emptyList()
        }

        val localIp = networkInfo.ipAddress
        val gateway = networkInfo.gateway
        val prefix = localIp.substringBeforeLast(".")

        // 1. Read current ARP table
        val arpDevices = readArpTable()

        // 2. Ping sweep (limited concurrency)
        val pinged = pingSweep(prefix, localIp)

        // Merge
        val allIps = (arpDevices.keys + pinged).toSet()

        val result = allIps.map { ip ->
            val mac = arpDevices[ip] ?: ""
            val isGateway = ip == gateway
            val isLocal = ip == localIp

            val existing = devicesCache[ip]
            val device = Device(
                ip = ip,
                mac = mac.ifBlank { existing?.mac ?: "" },
                hostname = existing?.hostname ?: "",
                vendor = existing?.vendor ?: lookupVendor(mac),
                isLocalDevice = isLocal,
                isGateway = isGateway,
                firstSeen = existing?.firstSeen ?: System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                nickname = existing?.nickname ?: "",
                notes = existing?.notes ?: "",
                tags = existing?.tags ?: emptyList(),
                isFavorite = existing?.isFavorite ?: false,
                isWatched = existing?.isWatched ?: false
            )
            devicesCache[ip] = device
            device
        }.sortedWith(
            compareByDescending<Device> { it.isGateway }
                .thenByDescending { it.isLocalDevice }
                .thenBy { it.ip }
        )

        result
    }

    private fun readArpTable(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // skip header
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val mac = parts[3]
                        if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                            result[ip] = mac.uppercase()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ARP table may be restricted on some devices
        }
        return result
    }

    private suspend fun pingSweep(prefix: String, localIp: String): Set<String> = coroutineScope {
        val alive = ConcurrentHashMap.newKeySet<String>()

        // Scan common range (1-254), but limit concurrency
        val jobs = (1..254).map { i ->
            async(Dispatchers.IO) {
                val ip = "$prefix.$i"
                if (ip == localIp) {
                    alive.add(ip)
                    return@async
                }
                try {
                    val address = InetAddress.getByName(ip)
                    // timeout 200ms — aggressive but reasonable
                    if (address.isReachable(200)) {
                        alive.add(ip)
                    }
                } catch (_: Exception) {
                }
            }
        }
        jobs.awaitAll()
        alive
    }

    private fun lookupVendor(mac: String): String {
        if (mac.isBlank() || mac.length < 8) return ""
        // Very small built-in OUI for common vendors (can be expanded later)
        val oui = mac.take(8).uppercase()
        return when {
            oui.startsWith("00:50:56") || oui.startsWith("00:0C:29") -> "VMware"
            oui.startsWith("00:1A:2B") || oui.startsWith("B8:27:EB") || oui.startsWith("DC:A6:32") -> "Raspberry Pi"
            oui.startsWith("00:1E:06") || oui.startsWith("00:25:9C") -> "Cisco"
            oui.startsWith("00:1B:63") || oui.startsWith("00:25:00") || oui.startsWith("F0:18:98") -> "Apple"
            oui.startsWith("00:1A:11") || oui.startsWith("3C:5A:B4") -> "Google"
            oui.startsWith("00:50:F2") -> "Microsoft"
            oui.startsWith("00:1D:7E") || oui.startsWith("00:26:5A") -> "Samsung"
            oui.startsWith("00:1E:10") || oui.startsWith("00:26:E8") -> "Xiaomi / Huawei"
            oui.startsWith("00:17:F2") || oui.startsWith("00:1C:B3") -> "TP-Link"
            oui.startsWith("00:1D:0F") || oui.startsWith("00:24:B2") -> "ASUS"
            oui.startsWith("00:18:F3") || oui.startsWith("00:22:6B") -> "D-Link"
            else -> ""
        }
    }

    /**
     * Simple TCP port scanner
     */
    suspend fun scanPorts(ip: String, ports: List<Int> = COMMON_PORTS): List<Int> = withContext(Dispatchers.IO) {
        coroutineScope {
            ports.map { port ->
                async {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(ip, port), 300)
                            port
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull().sorted()
        }
    }

    fun updateDevice(device: Device) {
        devicesCache[device.ip] = device
    }

    fun getCachedDevices(): List<Device> = devicesCache.values.toList()

    companion object {
        val COMMON_PORTS = listOf(
            21, 22, 23, 25, 53, 80, 110, 139, 143, 443,
            445, 993, 995, 1723, 3306, 3389, 5900, 8080, 8443, 9100
        )
    }
}
