package com.langhost.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.langhost.data.model.Device
import com.langhost.data.model.DeviceType
import com.langhost.data.model.NetworkInfo
import com.langhost.data.model.SecurityPortAuditPolicy
import com.langhost.data.model.PortAuditStatus
import com.langhost.data.model.ScanCoverage
import com.langhost.data.model.WifiNetwork
import com.langhost.data.network.Ipv4Subnet
import com.langhost.data.network.NetworkIdentity
import com.langhost.data.network.WifiChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import java.io.BufferedReader
import java.io.FileReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import java.util.concurrent.ConcurrentHashMap

class NetworkRepository(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val wifiInfoByNetwork = ConcurrentHashMap<Network, WifiInfo>()
    private val networkStateLock = Any()
    @Volatile private var publishedActiveNetwork: Network? = null
    private val wifiNetworkCallback = createWifiNetworkCallback()
    private val devicePreferences = DevicePreferences(appContext)

    private fun createWifiNetworkCallback(): ConnectivityManager.NetworkCallback {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
            ) {
                override fun onAvailable(network: Network) {
                    if (connectivityManager.activeNetwork == network) refreshNetworkInfo()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    cacheWifiInfo(network, capabilities)
                    if (connectivityManager.activeNetwork == network) refreshNetworkInfo()
                }

                override fun onLost(network: Network) {
                    wifiInfoByNetwork.remove(network)
                    refreshNetworkInfo()
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (connectivityManager.activeNetwork == network) refreshNetworkInfo()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    cacheWifiInfo(network, capabilities)
                    if (connectivityManager.activeNetwork == network) refreshNetworkInfo()
                }

                override fun onLost(network: Network) {
                    wifiInfoByNetwork.remove(network)
                    refreshNetworkInfo()
                }
            }
        }
    }

    private fun cacheWifiInfo(network: Network, capabilities: NetworkCapabilities) {
        // NetworkCapabilities.transportInfo was added in API 29. Android 8–9 use
        // WifiManager.connectionInfo in refreshNetworkInfo() instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            (capabilities.transportInfo as? WifiInfo)?.let { wifiInfoByNetwork[network] = it }
        } else {
            wifiInfoByNetwork.remove(network)
        }
    }
    private val appSettings = AppSettings(appContext)

    private val devicesCache = ConcurrentHashMap<String, Device>()
    private val scanMutex = Mutex()
    private val probeEngine = NetworkProbeEngine(
        scanTimeoutMs = { appSettings.getOnce().scanTimeoutMs },
        ensureCurrentNetworkScope = { expectedScope -> ensureCurrentNetworkScope(expectedScope) }
    )

    private val _networkInfo = MutableStateFlow(NetworkInfo())
    val networkInfo: StateFlow<NetworkInfo> = _networkInfo.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastScanTime = MutableStateFlow(0L)
    val lastScanTime: StateFlow<Long> = _lastScanTime.asStateFlow()

    private val _scanCoverage = MutableStateFlow(ScanCoverage())
    val scanCoverage: StateFlow<ScanCoverage> = _scanCoverage.asStateFlow()

    private val _lastDiff = MutableStateFlow<ScanDiffResult?>(null)
    val lastDiff: StateFlow<ScanDiffResult?> = _lastDiff.asStateFlow()

    private val _nearbyNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val nearbyNetworks: StateFlow<List<WifiNetwork>> = _nearbyNetworks.asStateFlow()

    private val _nearbyScanCompleted = MutableStateFlow(false)
    val nearbyScanCompleted: StateFlow<Boolean> = _nearbyScanCompleted.asStateFlow()

    private val _nearbyScanTime = MutableStateFlow(0L)
    val nearbyScanTime: StateFlow<Long> = _nearbyScanTime.asStateFlow()

    private val securityPortsScanMutex = Mutex()
    private val _securityPortsScanCompleted = MutableStateFlow(false)
    val securityPortsScanCompleted: StateFlow<Boolean> = _securityPortsScanCompleted.asStateFlow()
    private val _securityPortsScanTime = MutableStateFlow(0L)
    val securityPortsScanTime: StateFlow<Long> = _securityPortsScanTime.asStateFlow()

    private val _isSecurityScanning = MutableStateFlow(false)
    val isSecurityScanning: StateFlow<Boolean> = _isSecurityScanning.asStateFlow()

    // Register callbacks only after every StateFlow used by refreshNetworkInfo() is initialized.
    init {
        runCatching {
            // Observe the system default network across Wi-Fi, Ethernet, VPN and
            // other transports; a Wi-Fi-only request misses default-route changes.
            connectivityManager.registerDefaultNetworkCallback(wifiNetworkCallback)
        }
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    fun refreshNetworkInfo() {
        try {
            val network = connectivityManager.activeNetwork
            if (network == null) {
                publishNetworkInfo(NetworkInfo(), null)
                return
            }

            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val linkProperties = connectivityManager.getLinkProperties(network)
            if (capabilities == null || linkProperties == null) {
                publishNetworkInfo(NetworkInfo(), network)
                return
            }

            val localAddress = linkProperties.linkAddresses
                .asSequence()
                .mapNotNull { it.address as? java.net.Inet4Address }
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }

            val gateway = linkProperties.routes
                .asSequence()
                .filter { it.isDefaultRoute }
                .mapNotNull { it.gateway as? java.net.Inet4Address }
                .firstOrNull()

            val dnsServers = linkProperties.dnsServers
                .filterIsInstance<java.net.Inet4Address>()
                .mapNotNull { it.hostAddress }

            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val wifiInfo = if (isWifi) {
                wifiInfoByNetwork[network] ?: if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager.connectionInfo
                } else {
                    // getNetworkCapabilities() redacts location-sensitive WifiInfo fields on
                    // Android 12+. The callback above opts in only when permissions allow it.
                    capabilities.transportInfo as? WifiInfo
                }
            } else {
                null
            }

            val ssid = wifiInfo?.ssid
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID && it != "<unknown ssid>" }
                ?: "—"
            val bssid = wifiInfo?.bssid
                ?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }
                ?: "—"
            val frequency = wifiInfo?.frequency?.takeIf { it > 0 } ?: 0
            val prefixLength = linkProperties.linkAddresses
                .firstOrNull { it.address is java.net.Inet4Address && it.address == localAddress }
                ?.prefixLength
            val netmask = prefixLength?.let(::prefixToNetmask) ?: "0.0.0.0"
            val ip = localAddress?.hostAddress ?: "0.0.0.0"

            publishNetworkInfo(NetworkInfo(
                ssid = ssid,
                bssid = bssid,
                signalDbm = wifiInfo?.rssi?.takeIf { it in -127..0 } ?: 0,
                frequencyMhz = frequency,
                linkSpeedMbps = wifiInfo?.linkSpeed?.takeIf { it > 0 } ?: 0,
                channel = frequencyToChannel(frequency),
                security = wifiSecurityType(wifiInfo),
                ipAddress = ip,
                gateway = gateway?.hostAddress ?: "0.0.0.0",
                netmask = netmask,
                dns1 = dnsServers.getOrNull(0) ?: "0.0.0.0",
                dns2 = dnsServers.getOrNull(1) ?: "0.0.0.0",
                networkId = wifiInfo?.networkId ?: -1,
                interfaceName = linkProperties.interfaceName.orEmpty(),
                isConnected = localAddress != null && (isWifi ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            ), network)
        } catch (_: Exception) {
            publishNetworkInfo(NetworkInfo(), connectivityManager.activeNetwork)
        }
    }

    private fun networkScope(info: NetworkInfo): String? {
        if (!info.isConnected) return null
        return NetworkIdentity.scopeId(
            ssid = info.ssid,
            gateway = info.gateway,
            ipAddress = info.ipAddress,
            netmask = info.netmask
        )
    }

    private fun currentNetworkScope(): String? = networkScope(_networkInfo.value)

    /**
     * Refreshes the active Android network before each bounded scan batch.
     * A network switch cancels the operation instead of probing the old subnet
     * or attaching old-network results to a new device with the same IP.
     */
    private suspend fun ensureCurrentNetworkScope(expectedScope: String) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        refreshNetworkInfo()
        if (currentNetworkScope() != expectedScope) {
            throw CancellationException("Active network changed during scan")
        }
    }

    private fun publishNetworkInfo(next: NetworkInfo, activeNetwork: Network?) {
        synchronized(networkStateLock) {
            // Concurrent refreshes can finish out of order. Never let a snapshot for an
            // older Android Network overwrite the currently active default network.
            if (connectivityManager.activeNetwork != activeNetwork) return
            val previousScope = networkScope(_networkInfo.value)
            val nextScope = networkScope(next)
            if (previousScope != nextScope) {
                // Do not carry device, port, or nearby-AP evidence across logical networks.
                // Device customizations live in DataStore; the in-memory cache is scoped and bounded
                // to the active network so moving between many networks cannot grow it indefinitely.
                devicesCache.entries.removeAll { it.value.networkScope != nextScope }
                _devices.value = emptyList()
                _lastDiff.value = null
                _lastScanTime.value = 0L
                _scanCoverage.value = ScanCoverage()
                _securityPortsScanCompleted.value = false
                _securityPortsScanTime.value = 0L
                _nearbyNetworks.value = emptyList()
                _nearbyScanCompleted.value = false
                _nearbyScanTime.value = 0L
            }
            publishedActiveNetwork = activeNetwork
            _networkInfo.value = next
        }
    }

    /**
     * Serializes evidence commits with network publication and verifies Android's
     * current default Network as well as the logical scope. This closes the window
     * where a callback is queued but an old scan publishes against the old snapshot.
     */
    private inline fun commitIfCurrentNetwork(expectedScope: String, action: () -> Unit): Boolean =
        synchronized(networkStateLock) {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != publishedActiveNetwork || currentNetworkScope() != expectedScope) {
                false
            } else {
                action()
                true
            }
        }

    @SuppressLint("MissingPermission")
    suspend fun scanNearbyNetworks(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        refreshNetworkInfo()
        val scanScope = currentNetworkScope()
        val batch = withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                lateinit var receiver: BroadcastReceiver
                var registered = false

                fun unregister() {
                    if (registered) {
                        registered = false
                        runCatching { appContext.unregisterReceiver(receiver) }
                    }
                }

                fun finish(results: List<android.net.wifi.ScanResult>, updated: Boolean) {
                    if (finished.compareAndSet(false, true)) {
                        unregister()
                        if (continuation.isActive) continuation.resume(WifiScanBatch(results, updated))
                    }
                }

                receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true
                        val results = runCatching { wifiManager.scanResults ?: emptyList() }.getOrDefault(emptyList())
                        finish(results, updated)
                    }
                }

                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    appContext.registerReceiver(receiver, filter)
                }
                registered = true
                continuation.invokeOnCancellation {
                    if (finished.compareAndSet(false, true)) unregister()
                }

                @Suppress("DEPRECATION")
                val started = try {
                    wifiManager.startScan()
                } catch (error: Throwable) {
                    finished.set(true)
                    unregister()
                    throw error
                }
                if (!started) {
                    @Suppress("DEPRECATION")
                    finish(wifiManager.scanResults ?: emptyList(), false)
                }
            }
        } ?: WifiScanBatch(wifiManager.scanResults ?: emptyList(), false)

        val currentBssid = _networkInfo.value.bssid.takeIf { it.isNotBlank() && it != "—" }
        val mapped = batch.results.map { result ->
            val frequency = result.frequency
            WifiNetwork(
                ssid = result.SSID.orEmpty(),
                bssid = result.BSSID.orEmpty(),
                signalDbm = result.level,
                frequencyMhz = frequency,
                channel = WifiChannel.fromFrequencyMhz(frequency),
                security = WifiSecurity.classify(result.capabilities.orEmpty()),
                isCurrent = !result.BSSID.isNullOrBlank() && result.BSSID == currentBssid
            )
        }.filter { it.bssid.isNotBlank() }
            .distinctBy { it.bssid }
            .sortedByDescending { it.signalDbm }

        refreshNetworkInfo()
        if (currentNetworkScope() != scanScope) return@withContext emptyList()

        val completedAt = System.currentTimeMillis()
        val committed = scanScope?.let { expected ->
            commitIfCurrentNetwork(expected) {
                _nearbyNetworks.value = mapped
                if (batch.updated) _nearbyScanTime.value = completedAt
                _nearbyScanCompleted.value = batch.updated ||
                    (_nearbyScanTime.value > 0L && completedAt - _nearbyScanTime.value <= NEARBY_SCAN_FRESHNESS_MS)
            }
        } ?: false
        if (committed) mapped else emptyList()
    }

    private data class WifiScanBatch(
        val results: List<android.net.wifi.ScanResult>,
        val updated: Boolean
    )

    private fun wifiSecurityType(wifiInfo: WifiInfo?): String {
        if (wifiInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "—"
        return when (wifiInfo.currentSecurityType) {
            WifiInfo.SECURITY_TYPE_OPEN -> "Open"
            WifiInfo.SECURITY_TYPE_WEP -> "WEP"
            WifiInfo.SECURITY_TYPE_PSK -> "WPA-PSK"
            WifiInfo.SECURITY_TYPE_EAP -> "WPA-Enterprise"
            WifiInfo.SECURITY_TYPE_SAE -> "WPA3-SAE"
            WifiInfo.SECURITY_TYPE_OWE -> "OWE"
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3-Enterprise"
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3-Enterprise-192"
            WifiInfo.SECURITY_TYPE_WAPI_PSK -> "WAPI-PSK"
            WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI-CERT"
            WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2 -> "Passpoint"
            WifiInfo.SECURITY_TYPE_PASSPOINT_R3 -> "Passpoint R3"
            else -> "Unknown"
        }
    }

    private fun prefixToNetmask(prefixLength: Int): String {
        if (prefixLength !in 0..32) return "0.0.0.0"
        val mask = if (prefixLength == 0) 0L else (0xFFFF_FFFFL shl (32 - prefixLength)) and 0xFFFF_FFFFL
        return listOf(
            (mask shr 24) and 0xFF,
            (mask shr 16) and 0xFF,
            (mask shr 8) and 0xFF,
            mask and 0xFF
        ).joinToString(".")
    }

    private fun frequencyToChannel(freq: Int): Int = WifiChannel.fromFrequencyMhz(freq)

    private fun intToIp(value: Int): String {
        if (value == 0) return "0.0.0.0"
        return String.format("%d.%d.%d.%d", value and 0xff, value shr 8 and 0xff, value shr 16 and 0xff, value shr 24 and 0xff)
    }

    suspend fun scanDevices() = withContext(Dispatchers.IO) {
        if (!scanMutex.tryLock()) return@withContext
        _isScanning.value = true
        _scanCoverage.value = ScanCoverage()
        _securityPortsScanCompleted.value = false
        _securityPortsScanTime.value = 0L
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
            val networkScope = NetworkIdentity.scopeId(
                ssid = networkInfo.ssid,
                gateway = gateway,
                ipAddress = localIp,
                netmask = networkInfo.netmask
            )
            val legacyNetworkScope = NetworkIdentity.legacyScopeId(
                ssid = networkInfo.ssid,
                gateway = gateway,
                ipAddress = localIp,
                netmask = networkInfo.netmask
            )
            val subnet = runCatching {
                Ipv4Subnet.fromAddressAndNetmask(localIp, networkInfo.netmask)
            }.getOrNull()

            // /proc/net/arp may contain entries from other interfaces; keep only this subnet.
            val arpDevices = subnet?.let { currentSubnet ->
                readArpTable(networkInfo.interfaceName).filterKeys(currentSubnet::contains)
            }.orEmpty()
            val candidates = subnet?.hostAddresses(MAX_DISCOVERY_TARGETS, localIp).orEmpty()
            val discoveredIps = HostDiscovery.discover(
                candidates = candidates,
                localIp = localIp,
                maxConcurrency = MAX_CONCURRENT_HOST_PROBES,
                timeoutMs = timeoutMs,
                beforeBatch = { ensureCurrentNetworkScope(networkScope) }
            ) { ip, budgetMs -> probeEngine.probeHost(ip, budgetMs) }
            val allIps = (arpDevices.keys + discoveredIps + setOf(localIp, gateway).filter {
                it != "0.0.0.0" && it != "—" && it.isNotBlank()
            }).toSet()

            val now = System.currentTimeMillis()
            val completedCoverage = ScanCoverage(
                subnetCidr = subnet?.let { "${it.networkAddressString()}/${it.prefixLength}" }.orEmpty(),
                checkedAddresses = candidates.size,
                totalUsableAddresses = subnet?.usableHostCount ?: 0L,
                truncated = subnet != null && candidates.size.toLong() < subnet.usableHostCount,
                scannedAt = now,
                networkScope = networkScope
            )
            val onlineDevices = HostDiscovery.mapBounded(
                items = allIps.toList(),
                maxConcurrency = DEVICE_ENRICHMENT_CONCURRENCY,
                beforeBatch = { ensureCurrentNetworkScope(networkScope) }
            ) { ip ->
                val mac = arpDevices[ip] ?: ""
                val existingByIp = devicesCache.values.firstOrNull {
                    it.ip == ip && it.networkScope == networkScope
                }
                val macFinal = mac.ifBlank { existingByIp?.mac ?: "" }
                val randomMac = Device.isRandomMacAddress(macFinal)
                val stableId = when {
                    macFinal.isNotBlank() -> "${macFinal.uppercase()}@${networkScope}"
                    else -> "ip:${networkScope}:$ip"
                }
                val legacyIds = buildList {
                    if (macFinal.isNotBlank() && legacyNetworkScope != networkScope) {
                        add("${macFinal.uppercase()}@${legacyNetworkScope}")
                    } else if (macFinal.isBlank() && legacyNetworkScope != networkScope) {
                        add("ip:${legacyNetworkScope}:$ip")
                    }
                    if (macFinal.isNotBlank() && bssid.isNotBlank() && bssid != "—") {
                        add("${macFinal.uppercase()}@${bssid.uppercase()}")
                    }
                    if (macFinal.isNotBlank()) add(macFinal.uppercase())
                    add("ip:$ip")
                }
                val saved = devicePreferences.getCustomizationOnce(stableId, legacyIds)
                val existing = devicesCache[stableId] ?: existingByIp
                val firstSeen = when {
                    saved.firstSeen > 0 -> saved.firstSeen
                    existing != null && existing.firstSeen > 0 -> existing.firstSeen
                    else -> now
                }

                // Avoid reverse-DNS lookups in the scan hot path: platform name resolution can
                // block beyond coroutine timeouts. Use bounded NetBIOS discovery and cached data.
                val hostname = DeviceProbe.netBiosName(ip).ifBlank { existing?.hostname ?: "" }

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
                    nickname = saved.nickname.ifBlank { existing?.nickname ?: "" },
                    notes = saved.notes.ifBlank { existing?.notes ?: "" },
                    isFavorite = saved.isFavorite || (existing?.isFavorite == true),
                    isWatched = saved.isWatched || (existing?.isWatched == true),
                    networkBssid = bssid,
                    networkScope = networkScope,
                    deviceType = type,
                    httpFingerprint = existing?.httpFingerprint ?: "",
                    isRandomMac = randomMac,
                    tags = existing?.tags ?: emptyList(),
                    auditedPorts = existing?.auditedPorts ?: emptyList()
                )
            }

            val onlineIds = onlineDevices.map { it.stableId }.toSet()
            val offlineKept = devicesCache.values.filter { dev ->
                dev.stableId !in onlineIds &&
                    dev.networkScope == networkScope &&
                    (now - dev.lastSeen) < 24 * 60 * 60 * 1000
            }

            val result = (onlineDevices + offlineKept).sortedWith(
                compareByDescending<Device> { it.isOnline }
                    .thenByDescending { it.isGateway }
                    .thenByDescending { it.isLocalDevice }
                    .thenByDescending { it.isFavorite }
                    .thenBy { it.ip }
            )

            refreshNetworkInfo()
            val committed = commitIfCurrentNetwork(networkScope) {
                _lastDiff.value = ScanDiff.updateAndDiff(result, networkScope)
                _devices.value = result
                _lastScanTime.value = now
                onlineDevices.forEach { devicesCache[it.stableId] = it }
            }
            if (!committed) return@withContext
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
            // Cancelled or failed discovery must not be presented as completed security evidence.
            refreshNetworkInfo()
            commitIfCurrentNetwork(networkScope) {
                _scanCoverage.value = completedCoverage.copy(scannedAt = System.currentTimeMillis())
            }
        } finally {
            _isScanning.value = false
            scanMutex.unlock()
        }
    }

    private fun readArpTable(interfaceName: String): Map<String, String> {
        if (interfaceName.isBlank()) return emptyMap()
        return try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                parseArpTable(reader.readText(), interfaceName)
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun lookupVendor(mac: String): String {
        if (mac.isBlank() || mac.length < 8) return ""
        if (Device.isRandomMacAddress(mac)) return "Random MAC"
        return OUI_MAP[mac.take(8).uppercase()] ?: ""
    }

    /**
     * Checks the small set of security-relevant TCP services on every currently discovered
     * non-local host. Both host fan-out and per-host port fan-out are bounded.
     */
    suspend fun scanSecurityPorts(): Int = withContext(Dispatchers.IO) {
        // Serialize subnet discovery and the dedicated service audit, while the semaphore
        // below also caps sockets shared with manual device-detail scans.
        if (!scanMutex.tryLock()) return@withContext 0
        if (!securityPortsScanMutex.tryLock()) {
            scanMutex.unlock()
            return@withContext 0
        }
        _isSecurityScanning.value = true
        _securityPortsScanCompleted.value = false
        _securityPortsScanTime.value = 0L
        try {
            refreshNetworkInfo()
            val scanScope = currentNetworkScope() ?: return@withContext 0
            val coverage = _scanCoverage.value
            val now = System.currentTimeMillis()
            val coverageFresh = coverage.scannedAt > 0L && now >= coverage.scannedAt &&
                now - coverage.scannedAt <= NEARBY_SCAN_FRESHNESS_MS
            if (
                coverage.checkedAddresses <= 0 ||
                coverage.networkScope != scanScope ||
                !coverageFresh
            ) return@withContext 0

            val targets = _devices.value.filter {
                it.networkScope == scanScope && it.isOnline && !it.isLocalDevice
            }
            val scanned = HostDiscovery.mapBounded(
                items = targets,
                maxConcurrency = SECURITY_HOST_SCAN_CONCURRENCY,
                beforeBatch = { ensureCurrentNetworkScope(scanScope) }
            ) { device ->
                val evidence = probeEngine.scanSecurityPortEvidence(device.ip, SecurityPortAuditPolicy.ports, scanScope)
                // Keep focused security evidence separate from the broader device-detail inventory.
                device.copy(
                    auditedPorts = evidence.filter { it.status == PortAuditStatus.OPEN }.map { it.port },
                    auditedPortResults = evidence
                )
            }

            // A network switch during the port scan invalidates its results.
            refreshNetworkInfo()
            val scannedById = scanned.associateBy { it.stableId }
            var auditedPortCount = 0
            val committed = commitIfCurrentNetwork(scanScope) {
                // Merge only the dedicated audit result into the latest device snapshots. A manual
                // detail scan, DHCP address change or customization may have updated the device
                // while this scan ran; endpoint identity must still match before evidence is attached.
                _devices.update { current ->
                    current.map { device ->
                        val scannedDevice = scannedById[device.stableId]
                        if (scannedDevice != null && device.hasSameEndpointAs(scannedDevice)) {
                            device.copy(
                                auditedPorts = scannedDevice.auditedPorts,
                                auditedPortResults = scannedDevice.auditedPortResults
                            )
                        } else device
                    }
                }
                _devices.value.filter { it.networkScope == scanScope }.forEach { device ->
                    devicesCache[device.stableId] = device
                }
                _securityPortsScanCompleted.value = true
                _securityPortsScanTime.value = System.currentTimeMillis()
                auditedPortCount = scanned.sumOf { it.auditedPorts.size }
            }
            if (committed) auditedPortCount else 0
        } finally {
            _isSecurityScanning.value = false
            securityPortsScanMutex.unlock()
            scanMutex.unlock()
        }
    }

    /**
     * Scans only a device snapshot that still belongs to the active logical network.
     * Returns null when the device became stale or the network changed during the scan.
     */
    suspend fun scanDevicePorts(device: Device): List<Int>? = withContext(Dispatchers.IO) {
        refreshNetworkInfo()
        val expectedScope = currentNetworkScope() ?: return@withContext null
        if (device.networkScope != expectedScope) return@withContext null
        val currentDevice = _devices.value.firstOrNull {
            it.stableId == device.stableId && it.networkScope == expectedScope
        } ?: return@withContext null

        val ports = probeEngine.scanPorts(currentDevice.ip, COMMON_PORTS, expectedScope)
        ensureCurrentNetworkScope(expectedScope)
        updateDevicePorts(currentDevice.stableId, expectedScope, currentDevice.ip, ports)
        ports
    }

    private suspend fun updateDevicePorts(
        stableId: String,
        expectedScope: String,
        expectedIp: String,
        ports: List<Int>
    ) {
        ensureCurrentNetworkScope(expectedScope)
        var updated: Device? = null
        val committed = commitIfCurrentNetwork(expectedScope) {
            val current = _devices.value.firstOrNull {
                it.stableId == stableId && it.networkScope == expectedScope && it.ip == expectedIp
            } ?: return@commitIfCurrentNetwork
            val next = current.withUpdatedPorts(ports)
            devicesCache[next.stableId] = next
            _devices.update { list ->
                list.map { if (it.hasSameEndpointAs(next)) next else it }
            }
            updated = next
        }
        if (committed) updated?.let { enrichDevice(it) }
    }

    /** Enrichment results are committed only if the originating network/device is still current. */
    private suspend fun enrichDevice(device: Device) {
        ensureCurrentNetworkScope(device.networkScope)
        val (nb, http) = DeviceProbe.enrichHostnamesAndFingerprint(device.ip, device.openPorts)
        ensureCurrentNetworkScope(device.networkScope)
        commitIfCurrentNetwork(device.networkScope) {
            val current = _devices.value.firstOrNull { device.hasSameEndpointAs(it) } ?: return@commitIfCurrentNetwork
            val hostname = current.hostname.ifBlank { nb }
            val updated = current.copy(
                hostname = hostname,
                httpFingerprint = http.ifBlank { current.httpFingerprint },
                osGuess = Device.guessOs(current.openPorts, current.vendor, hostname),
                deviceType = Device.guessType(current.openPorts, current.vendor, hostname, current.isGateway)
            )
            devicesCache[updated.stableId] = updated
            _devices.update { list ->
                list.map {
                    if (device.hasSameEndpointAs(it)) updated else it
                }
            }
        }
    }

    suspend fun updateDeviceCustomization(stableId: String, customization: DeviceCustomization) {
        if (stableId.isBlank()) return

        refreshNetworkInfo()
        val expectedScope = currentNetworkScope()
        val target = expectedScope?.let { scope ->
            _devices.value.firstOrNull { it.stableId == stableId && it.networkScope == scope }
        }

        // Persist by stable identity even if the detail screen became stale, but never apply
        // that old screen's edits to a different device that now owns the same IP address.
        devicePreferences.saveCustomization(stableId, customization)
        if (target == null || expectedScope == null) return

        refreshNetworkInfo()
        commitIfCurrentNetwork(expectedScope) {
            _devices.update { list ->
                list.map { device ->
                    if (device.stableId == stableId && device.networkScope == expectedScope) {
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
    }

    fun getDevice(ip: String): Device? {
        val scope = currentNetworkScope() ?: return null
        return _devices.value.firstOrNull { it.ip == ip && it.networkScope == scope }
            ?: devicesCache.values.firstOrNull { it.ip == ip && it.networkScope == scope }
    }

    suspend fun getKnownIds(): Set<String> = devicePreferences.getKnownIds()

    companion object {
        private const val MAX_DISCOVERY_TARGETS = 1024
        private const val MAX_CONCURRENT_HOST_PROBES = 32
        private const val DEVICE_ENRICHMENT_CONCURRENCY = 16
        private const val SECURITY_HOST_SCAN_CONCURRENCY = 4
        private const val NEARBY_SCAN_FRESHNESS_MS = 5 * 60 * 1000L

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
