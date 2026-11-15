package com.langhost.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.langhost.data.model.Device
import com.langhost.data.model.DeviceType
import com.langhost.data.model.NetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val appSettings = AppSettings(appContext)

    private val devicesCache = ConcurrentHashMap<String, Device>()

    private val _networkInfo = MutableStateFlow(NetworkInfo())
    val networkInfo: StateFlow<NetworkInfo> = _networkInfo.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastScanTime = MutableStateFlow(0L)
    val lastScanTime: StateFlow<Long> = _lastScanTime.asStateFlow()

    private val _lastDiff = MutableStateFlow<ScanDiffResult?>(null)
    val lastDiff: StateFlow<ScanDiffResult?> = _lastDiff.asStateFlow()

    @SuppressLint("MissingPermission", "HardwareIds")
    fun refreshNetworkInfo() {
        try {
            val wifiInfo = wifiManager.connectionInfo
            val dhcpInfo = wifiManager.dhcpInfo
            val ssid = wifiInfo.ssid?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: "—"
            val bssid = wifiInfo.bssid ?: "—"
            val frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) wifiInfo.frequency else 0
            val ip = intToIp(wifiInfo.ipAddress)
            _networkInfo.value = NetworkInfo(
                ssid = ssid,
                bssid = bssid,
                signalDbm = wifiInfo.rssi,
                frequencyMhz = frequency,
                linkSpeedMbps = wifiInfo.linkSpeed,
                channel = frequencyToChannel(frequency),
                security = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "WPA2/WPA3" else "WPA/WPA2",
                ipAddress = ip,
                gateway = intToIp(dhcpInfo.gateway),
                netmask = intToIp(dhcpInfo.netmask),
                dns1 = intToIp(dhcpInfo.dns1),
                dns2 = intToIp(dhcpInfo.dns2),
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
        return String.format("%d.%d.%d.%d", value and 0xff, value shr 8 and 0xff, value shr 16 and 0xff, value shr 24 and 0xff)
    }

    private fun hostRangeFromNetmask(netmask: String): IntRange {
        return try {
            val parts = netmask.split(".").map { it.toInt() }
            if (parts.size != 4) return 1..254
            var bits = 0
            for (p in parts) bits += Integer.bitCount(p and 0xFF)
            val hostBits = 32 - bits
            if (hostBits <= 0 || hostBits > 16) return 1..254
            1..(1 shl hostBits).minus(2).coerceAtMost(254)
        } catch (_: Exception) {
            1..254
        }
    }

    suspend fun scanDevices() = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        _isScanning.value = true
        try {
            refreshNetworkInfo()
            val networkInfo = _networkInfo.value
            if (!networkInfo.isConnected || networkInfo.ipAddress == "—" || networkInfo.ipAddress == "0.0.0.0") {
                return@withContext
            }

            val settings = appSettings.getOnce()
            val timeoutMs = settings.scanTimeoutMs
            val localIp = networkInfo.ipAddress
            val gateway = networkInfo.gateway
            val bssid = networkInfo.bssid
            val prefix = localIp.substringBeforeLast(".")
            val range = hostRangeFromNetmask(networkInfo.netmask)

            val arpDevices = readArpTable()
            val discoveredIps = discoverHosts(prefix, localIp, range, timeoutMs)
            val allIps = (arpDevices.keys + discoveredIps + setOf(localIp, gateway).filter {
                it != "0.0.0.0" && it != "—" && it.isNotBlank()
            }).toSet()

            val now = System.currentTimeMillis()
            val onlineDevices = coroutineScope {
                allIps.map { ip ->
                    async {
                        val mac = arpDevices[ip] ?: ""
                        val randomMac = Device.isRandomMacAddress(mac)
                        val tempId = when {
                            mac.isNotBlank() && bssid.isNotBlank() && bssid != "—" ->
                                "${mac.uppercase()}@${bssid.uppercase()}"
                            mac.isNotBlank() -> mac.uppercase()
                            else -> "ip:$ip"
                        }
                        val saved = devicePreferences.getCustomizationOnce(tempId)
                        val savedMac = if (mac.isNotBlank()) devicePreferences.getCustomizationOnce(mac.uppercase()) else DeviceCustomization()
                        val existing = devicesCache[tempId]
                        val firstSeen = when {
                            saved.firstSeen > 0 -> saved.firstSeen
                            existing != null && existing.firstSeen > 0 -> existing.firstSeen
                            else -> now
                        }

                        var hostname = withTimeoutOrNull(250L) { resolveHostname(ip) } ?: ""
                        if (hostname.isBlank()) {
                            hostname = DeviceProbe.netBiosName(ip).ifBlank { existing?.hostname ?: "" }
                        }

                        val macFinal = mac.ifBlank { existing?.mac ?: "" }
                        val vendor = if (randomMac) "Random MAC" else lookupVendor(macFinal)
                        val ports = existing?.openPorts ?: emptyList()
                        val type = Device.guessType(ports, vendor, hostname, ip == gateway)

                        Device(
                            ip = ip,
                            mac = macFinal,
                            hostname = hostname.ifBlank { existing?.hostname ?: "" },
                            vendor = vendor.ifBlank { existing?.vendor ?: "" },
                            isLocalDevice = ip == localIp,
                            isGateway = ip == gateway,
                            firstSeen = firstSeen,
                            lastSeen = now,
                            openPorts = ports,
                            osGuess = existing?.osGuess ?: Device.guessOs(ports, vendor, hostname),
                            nickname = saved.nickname.ifBlank { savedMac.nickname.ifBlank { existing?.nickname ?: "" } },
                            notes = saved.notes.ifBlank { savedMac.notes.ifBlank { existing?.notes ?: "" } },
                            isFavorite = saved.isFavorite || savedMac.isFavorite || (existing?.isFavorite == true),
                            isWatched = saved.isWatched || savedMac.isWatched || (existing?.isWatched == true),
                            networkBssid = bssid,
                            deviceType = type,
                            httpFingerprint = existing?.httpFingerprint ?: "",
                            isRandomMac = randomMac,
                            tags = existing?.tags ?: emptyList()
                        ).also { devicesCache[it.stableId] = it }
                    }
                }.awaitAll()
            }

            val onlineIds = onlineDevices.map { it.stableId }.toSet()
            val offlineKept = devicesCache.values.filter { dev ->
                dev.stableId !in onlineIds &&
                    (dev.networkBssid == bssid || dev.networkBssid.isBlank()) &&
                    (now - dev.lastSeen) < 24 * 60 * 60 * 1000
            }

            val result = (onlineDevices + offlineKept).sortedWith(
                compareByDescending<Device> { it.isOnline }
                    .thenByDescending { it.isGateway }
                    .thenByDescending { it.isLocalDevice }
                    .thenByDescending { it.isFavorite }
                    .thenBy { it.ip }
            )

            _lastDiff.value = ScanDiff.updateAndDiff(result)
            _devices.value = result
            _lastScanTime.value = now
            devicePreferences.saveKnownIds(onlineDevices.map { it.stableId })

            onlineDevices.forEach { d ->
                devicePreferences.saveCustomization(
                    d.stableId,
                    DeviceCustomization(
                        nickname = d.nickname,
                        notes = d.notes,
                        isFavorite = d.isFavorite,
                        isWatched = d.isWatched,
                        firstSeen = d.firstSeen
                    )
                )
            }
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

    private suspend fun discoverHosts(prefix: String, localIp: String, range: IntRange, timeoutMs: Int): Set<String> =
        coroutineScope {
            val alive = ConcurrentHashMap.newKeySet<String>()
            val portsToTry = listOf(80, 443, 8080, 22, 135, 445, 139, 53, 3389)
            val t = timeoutMs.coerceIn(50, 500)
            range.map { i ->
                async(Dispatchers.IO) {
                    val ip = "$prefix.$i"
                    if (ip == localIp) {
                        alive.add(ip)
                        return@async
                    }
                    try {
                        if (InetAddress.getByName(ip).isReachable(t)) {
                            alive.add(ip)
                            return@async
                        }
                        for (port in portsToTry) {
                            try {
                                Socket().use { it.connect(InetSocketAddress(ip, port), t); alive.add(ip); return@async }
                            } catch (_: Exception) {
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }.awaitAll()
            alive
        }

    private fun resolveHostname(ip: String): String = try {
        val host = InetAddress.getByName(ip).canonicalHostName
        if (host != null && host != ip && !host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) host else ""
    } catch (_: Exception) {
        ""
    }

    private fun lookupVendor(mac: String): String {
        if (mac.isBlank() || mac.length < 8) return ""
        if (Device.isRandomMacAddress(mac)) return "Random MAC"
        return OUI_MAP[mac.take(8).uppercase()] ?: ""
    }

    suspend fun scanPorts(ip: String, ports: List<Int> = COMMON_PORTS): List<Int> = withContext(Dispatchers.IO) {
        val t = (appSettings.getOnce().scanTimeoutMs * 2).coerceIn(100, 800)
        coroutineScope {
            ports.map { port ->
                async {
                    try {
                        Socket().use { it.connect(InetSocketAddress(ip, port), t); port }
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull().sorted()
        }
    }

    /** After port scan: HTTP fingerprint + refresh type */
    suspend fun enrichDevice(ip: String) {
        val device = getDevice(ip) ?: return
        val (nb, http) = DeviceProbe.enrichHostnamesAndFingerprint(ip, device.openPorts)
        val hostname = device.hostname.ifBlank { nb }
        val updated = device.copy(
            hostname = hostname,
            httpFingerprint = http.ifBlank { device.httpFingerprint },
            osGuess = Device.guessOs(device.openPorts, device.vendor, hostname),
            deviceType = Device.guessType(device.openPorts, device.vendor, hostname, device.isGateway)
        )
        devicesCache[updated.stableId] = updated
        _devices.update { list -> list.map { if (it.ip == ip) updated else it } }
    }

    suspend fun updateDevicePorts(ip: String, ports: List<Int>) {
        _devices.update { list ->
            list.map { device ->
                if (device.ip == ip) {
                    val updated = device.withUpdatedPorts(ports)
                    devicesCache[updated.stableId] = updated
                    updated
                } else device
            }
        }
        enrichDevice(ip)
    }

    suspend fun updateDeviceCustomization(stableId: String, customization: DeviceCustomization) {
        devicePreferences.saveCustomization(stableId, customization)
        _devices.update { list ->
            list.map { device ->
                if (device.stableId == stableId || device.ip == stableId.removePrefix("ip:")) {
                    device.copy(
                        nickname = customization.nickname,
                        notes = customization.notes,
                        isFavorite = customization.isFavorite,
                        isWatched = customization.isWatched
                    ).also { devicesCache[it.stableId] = it }
                } else device
            }
        }
    }

    fun getDevice(ip: String): Device? =
        _devices.value.find { it.ip == ip } ?: devicesCache.values.find { it.ip == ip }

    suspend fun getKnownIds(): Set<String> = devicePreferences.getKnownIds()

    companion object {
        val COMMON_PORTS = listOf(
            21, 22, 23, 25, 53, 80, 110, 111, 135, 139, 143,
            443, 445, 548, 993, 995, 1723, 3306, 3389, 5900, 8008, 8009, 8080, 8443, 9100
        )

        private val OUI_MAP = mapOf(
            "00:50:56" to "VMware", "00:0C:29" to "VMware", "00:05:69" to "VMware",
            "B8:27:EB" to "Raspberry Pi", "DC:A6:32" to "Raspberry Pi", "E4:5F:01" to "Raspberry Pi",
            "00:1B:63" to "Apple", "00:25:00" to "Apple", "F0:18:98" to "Apple",
            "AC:BC:32" to "Apple", "00:17:F2" to "Apple", "00:1C:B3" to "Apple",
            "00:1A:11" to "Google", "3C:5A:B4" to "Google", "F4:F5:D8" to "Google",
            "00:50:F2" to "Microsoft", "00:15:5D" to "Microsoft",
            "08:D4:2B" to "Samsung", "00:26:5A" to "Samsung",
            "00:1E:10" to "Huawei", "28:6C:07" to "Xiaomi",
            "50:C7:BF" to "TP-Link", "00:27:19" to "TP-Link",
            "2C:4D:54" to "ASUS", "00:1D:0F" to "ASUS",
            "00:22:6B" to "Linksys", "00:1D:7E" to "Cisco-Linksys", "00:25:9C" to "Cisco",
            "00:24:01" to "D-Link", "04:92:26" to "Nokia"
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
