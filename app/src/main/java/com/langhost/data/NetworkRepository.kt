package com.langhost.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.langhost.data.model.Device
import com.langhost.data.model.NetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class NetworkRepository(context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val devicePreferences = DevicePreferences(appContext)

    private val devicesCache = ConcurrentHashMap<String, Device>()

    private val _networkInfo = MutableStateFlow(NetworkInfo())
    val networkInfo: StateFlow<NetworkInfo> = _networkInfo.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastScanTime = MutableStateFlow(0L)
    val lastScanTime: StateFlow<Long> = _lastScanTime.asStateFlow()

    @SuppressLint("MissingPermission", "HardwareIds")
    fun refreshNetworkInfo() {
        try {
            val wifiInfo = wifiManager.connectionInfo
            val dhcpInfo = wifiManager.dhcpInfo

            val ssid = wifiInfo.ssid
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: "—"

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

            _networkInfo.value = NetworkInfo(
                ssid = ssid,
                bssid = bssid,
                signalDbm = signalDbm,
                frequencyMhz = frequency,
                linkSpeedMbps = linkSpeed,
                channel = channel,
                security = "WPA/WPA2/WPA3",
                ipAddress = ip,
                gateway = gateway,
                netmask = netmask,
                dns1 = dns1,
                dns2 = dns2,
                networkId = wifiInfo.networkId,
                isConnected = wifiInfo.networkId != -1 && ip != "0.0.0.0"
            )
        } catch (_: Exception) {
            _networkInfo.value = NetworkInfo()
        }
    }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        else -> 0
    }

    private fun intToIp(value: Int): String {
        if (value == 0) return "0.0.0.0"
        return String.format(
            "%d.%d.%d.%d",
            value and 0xff,
            value shr 8 and 0xff,
            value shr 16 and 0xff,
            value shr 24 and 0xff
        )
    }

    suspend fun scanDevices() {
        if (_isScanning.value) return

        _isScanning.value = true
        try {
            refreshNetworkInfo()
            val networkInfo = _networkInfo.value

            if (!networkInfo.isConnected || networkInfo.ipAddress == "—" || networkInfo.ipAddress == "0.0.0.0") {
                _devices.value = emptyList()
                return
            }

            val localIp = networkInfo.ipAddress
            val gateway = networkInfo.gateway
            val prefix = localIp.substringBeforeLast(".")

            // Parallel discovery methods
            val arpDevices = withContext(Dispatchers.IO) { readArpTable() }
            val pinged = discoverHosts(prefix, localIp)

            // Always include gateway and local
            val allIps = (arpDevices.keys + pinged + setOf(localIp, gateway).filter {
                it != "0.0.0.0" && it != "—" && it.isNotBlank()
            }).toSet()

            val result = allIps.map { ip ->
                val mac = arpDevices[ip] ?: devicesCache[ip]?.mac ?: ""
                val existing = devicesCache[ip]

                // CRITICAL: load saved customization from DataStore
                val saved = try {
                    devicePreferences.getCustomization(ip).first()
                } catch (_: Exception) {
                    DeviceCustomization()
                }

                val nickname = saved.nickname.ifBlank { existing?.nickname ?: "" }
                val notes = saved.notes.ifBlank { existing?.notes ?: "" }
                val isFavorite = saved.isFavorite || (existing?.isFavorite == true)
                val isWatched = saved.isWatched || (existing?.isWatched == true)

                Device(
                    ip = ip,
                    mac = mac,
                    hostname = existing?.hostname?.takeIf { it.isNotBlank() } ?: resolveHostname(ip),
                    vendor = existing?.vendor?.takeIf { it.isNotBlank() } ?: lookupVendor(mac),
                    isLocalDevice = ip == localIp,
                    isGateway = ip == gateway,
                    firstSeen = existing?.firstSeen ?: System.currentTimeMillis(),
                    lastSeen = System.currentTimeMillis(),
                    openPorts = existing?.openPorts ?: emptyList(),
                    osGuess = existing?.osGuess ?: "",
                    nickname = nickname,
                    notes = notes,
                    isFavorite = isFavorite,
                    isWatched = isWatched
                ).also { devicesCache[ip] = it }
            }.sortedWith(
                compareByDescending<Device> { it.isGateway }
                    .thenByDescending { it.isLocalDevice }
                    .thenByDescending { it.isFavorite }
                    .thenByDescending { it.isWatched }
                    .thenBy { it.ip }
            )

            _devices.value = result
            _lastScanTime.value = System.currentTimeMillis()

            // Persist known IPs for Watch Mode
            devicePreferences.saveKnownIps(result.map { it.ip })
        } finally {
            _isScanning.value = false
        }
    }

    private fun readArpTable(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine()
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
        } catch (_: Exception) {
        }
        return result
    }

    /**
     * Hybrid discovery: ICMP isReachable + TCP connect to common ports.
     * isReachable often fails on Android without root.
     */
    private suspend fun discoverHosts(prefix: String, localIp: String): Set<String> = coroutineScope {
        val alive = ConcurrentHashMap.newKeySet<String>()
        val portsToTry = listOf(80, 443, 8080, 22, 135, 445, 139, 53, 3389)

        val jobs = (1..254).map { i ->
            async(Dispatchers.IO) {
                val ip = "$prefix.$i"
                if (ip == localIp) {
                    alive.add(ip)
                    return@async
                }
                try {
                    // Method 1: ICMP-ish
                    val address = InetAddress.getByName(ip)
                    if (address.isReachable(120)) {
                        alive.add(ip)
                        return@async
                    }
                    // Method 2: TCP connect fallback (works when ICMP is blocked)
                    for (port in portsToTry) {
                        try {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(ip, port), 100)
                                alive.add(ip)
                                return@async
                            }
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        jobs.awaitAll()
        alive
    }

    private fun resolveHostname(ip: String): String {
        return try {
            val address = InetAddress.getByName(ip)
            val host = address.canonicalHostName
            if (host != null && host != ip && !host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) host else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun lookupVendor(mac: String): String {
        if (mac.isBlank() || mac.length < 8) return ""
        val oui = mac.take(8).uppercase()
        return when {
            oui.startsWith("00:50:56") || oui.startsWith("00:0C:29") || oui.startsWith("00:05:69") -> "VMware"
            oui.startsWith("B8:27:EB") || oui.startsWith("DC:A6:32") || oui.startsWith("E4:5F:01") -> "Raspberry Pi"
            oui.startsWith("00:1A:2B") || oui.startsWith("00:1E:06") || oui.startsWith("00:25:9C") -> "Cisco"
            oui.startsWith("00:1B:63") || oui.startsWith("00:25:00") || oui.startsWith("F0:18:98") || oui.startsWith("AC:BC:32") -> "Apple"
            oui.startsWith("00:1A:11") || oui.startsWith("3C:5A:B4") || oui.startsWith("F4:F5:D8") -> "Google"
            oui.startsWith("00:50:F2") || oui.startsWith("00:15:5D") -> "Microsoft"
            oui.startsWith("00:1D:7E") || oui.startsWith("00:26:5A") || oui.startsWith("08:D4:2B") -> "Samsung"
            oui.startsWith("00:1E:10") || oui.startsWith("00:26:E8") || oui.startsWith("28:6C:07") -> "Xiaomi"
            oui.startsWith("00:17:F2") || oui.startsWith("00:1C:B3") || oui.startsWith("50:C7:BF") -> "TP-Link"
            oui.startsWith("00:1D:0F") || oui.startsWith("00:24:B2") || oui.startsWith("2C:4D:54") -> "ASUS"
            oui.startsWith("00:18:F3") || oui.startsWith("00:22:6B") -> "D-Link"
            oui.startsWith("00:1E:8C") || oui.startsWith("00:24:01") -> "Huawei"
            oui.startsWith("00:26:BB") || oui.startsWith("04:92:26") -> "Nokia"
            else -> ""
        }
    }

    suspend fun scanPorts(ip: String, ports: List<Int> = COMMON_PORTS): List<Int> = withContext(Dispatchers.IO) {
        coroutineScope {
            ports.map { port ->
                async {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(ip, port), 400)
                            port
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull().sorted()
        }
    }

    suspend fun updateDevicePorts(ip: String, ports: List<Int>) {
        _devices.update { list ->
            list.map { device ->
                if (device.ip == ip) {
                    val updated = device.withUpdatedPorts(ports)
                    devicesCache[ip] = updated
                    updated
                } else device
            }
        }
    }

    suspend fun updateDeviceCustomization(ip: String, customization: DeviceCustomization) {
        devicePreferences.saveCustomization(ip, customization)

        _devices.update { list ->
            list.map { device ->
                if (device.ip == ip) {
                    device.copy(
                        nickname = customization.nickname,
                        notes = customization.notes,
                        isFavorite = customization.isFavorite,
                        isWatched = customization.isWatched
                    ).also { devicesCache[ip] = it }
                } else device
            }
        }
    }

    fun getDevice(ip: String): Device? = devicesCache[ip] ?: _devices.value.find { it.ip == ip }

    suspend fun getKnownIps(): Set<String> = devicePreferences.getKnownIps()

    companion object {
        val COMMON_PORTS = listOf(
            21, 22, 23, 25, 53, 80, 110, 111, 135, 139, 143,
            443, 445, 548, 993, 995, 1723, 3306, 3389, 5900, 8080, 8443, 9100
        )

        @Volatile
        private var INSTANCE: NetworkRepository? = null

        fun getInstance(context: Context): NetworkRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkRepository(context).also { INSTANCE = it }
            }
        }
    }
}
