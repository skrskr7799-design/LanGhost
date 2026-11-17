package com.langhost.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import com.langhost.data.model.Device
import com.langhost.data.model.DeviceType
import com.langhost.data.model.NetworkInfo
import com.langhost.data.model.SecurityPortAuditPolicy
import com.langhost.data.model.PortAuditStatus
import com.langhost.data.model.ScanCoverage
import com.langhost.data.model.DiscoverySourceStatus
import com.langhost.data.model.WifiNetwork
import com.langhost.data.network.NetworkIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import java.io.BufferedReader
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean


private data class ArpTableReadResult(
    val entries: Map<String, String>,
    val available: Boolean
)

class NetworkRepository(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val nsdDiscovery = AndroidNsdDiscovery(appContext)
    private val ssdpDiscovery = AndroidSsdpDiscovery()
    private val icmpDiscovery = AndroidIcmpDiscovery()
    private val wifiScanCoordinator = WifiScanCoordinator(
        AndroidWifiScanPlatform(appContext, wifiManager) {
            _networkInfo.value.bssid.takeIf { it.isNotBlank() && it != "—" }
        }
    )
    private val networkStateLock = Any()
    @Volatile private var publishedActiveNetwork: Network? = null
    private val networkMonitor = AndroidNetworkMonitor(connectivityManager, wifiManager) {
        refreshNetworkInfo()
    }
    private val devicePreferences = DevicePreferences(appContext)
    private val deviceEnrichmentEngine = DeviceEnrichmentEngine(
        loadCustomization = { stableId, legacyIds ->
            devicePreferences.getCustomizationOnce(stableId, legacyIds)
        },
        resolveHostname = { ip -> DeviceProbe.netBiosName(ip) },
        lookupVendor = { mac -> lookupVendor(mac) }
    )

    private val appSettings = AppSettings(appContext)

    private val devicesCache = ConcurrentHashMap<String, Device>()
    private val scanMutex = Mutex()
    private val probeEngine = NetworkProbeEngine(
        scanTimeoutMs = { appSettings.getOnce().scanTimeoutMs },
        ensureCurrentNetworkScope = { expectedScope -> ensureCurrentNetworkScope(expectedScope) }
    )
    private val discoveryEngine = NetworkDiscoveryEngine(
        probeHost = { ip, budgetMs -> probeEngine.probeHost(ip, budgetMs) },
        ensureCurrentNetworkScope = { expectedScope -> ensureCurrentNetworkScope(expectedScope) }
    )
    private val deviceLifecycleEngine = DeviceLifecycleEngine()

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

    // Start Android callbacks only after every StateFlow is initialized.
    init {
        networkMonitor.start()
    }

    fun refreshNetworkInfo() {
        val snapshot = networkMonitor.readSnapshot()
        publishNetworkInfo(snapshot.info, snapshot.activeNetwork)
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

    suspend fun scanNearbyNetworks(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        refreshNetworkInfo()
        val scanScope = currentNetworkScope()
        val batch = wifiScanCoordinator.scan()

        refreshNetworkInfo()
        if (currentNetworkScope() != scanScope) return@withContext emptyList()

        val completedAt = System.currentTimeMillis()
        val committed = scanScope?.let { expected ->
            commitIfCurrentNetwork(expected) {
                _nearbyNetworks.value = batch.results
                if (batch.updated) _nearbyScanTime.value = completedAt
                _nearbyScanCompleted.value = batch.updated ||
                    (_nearbyScanTime.value > 0L && completedAt - _nearbyScanTime.value <= NEARBY_SCAN_FRESHNESS_MS)
            }
        } ?: false
        if (committed) batch.results else emptyList()
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
            val activeNetwork = publishedActiveNetwork
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
            // Keep ARP parsing and interface selection in the Android repository;
            // discovery orchestration itself is platform-independent and independently tested.
            val arpReadable = AtomicBoolean(false)
            val initialArp = readArpTable(networkInfo.interfaceName).also { snapshot ->
                if (snapshot.available) arpReadable.set(true)
            }
            val discovery = discoveryEngine.discover(
                localIp = localIp,
                gateway = gateway,
                netmask = networkInfo.netmask,
                arpEntries = initialArp.entries,
                networkScope = networkScope,
                timeoutMs = timeoutMs,
                maxTargets = MAX_DISCOVERY_TARGETS,
                maxConcurrency = MAX_CONCURRENT_HOST_PROBES,
                refreshArpEntries = {
                    readArpTable(networkInfo.interfaceName).also { snapshot ->
                        if (snapshot.available) arpReadable.set(true)
                    }.entries
                },
                discoverNsdServiceIps = { nsdDiscovery.discover(activeNetwork) },
                discoverSsdpServiceIps = { ssdpDiscovery.discover(activeNetwork) },
                discoverHostReachabilityIps = { candidates ->
                    icmpDiscovery.discover(
                        candidateIps = candidates,
                        interfaceName = networkInfo.interfaceName,
                        activeNetwork = activeNetwork,
                        maxConcurrency = MAX_CONCURRENT_HOST_PROBES,
                        timeoutMs = 250,
                        beforeWindow = { ensureCurrentNetworkScope(networkScope) }
                    )
                }
            )
            val arpDevices = discovery.arpAddresses
            val allIps = discovery.ips
            val now = System.currentTimeMillis()
            val completedCoverage = discovery.coverage.copy(
                scannedAt = now,
                arpAvailable = arpReadable.get(),
                arpStatus = arpSourceStatus(arpReadable.get(), discovery.arpAddresses)
            )
            val onlineDevices = HostDiscovery.mapBounded(
                items = allIps.toList(),
                maxConcurrency = DEVICE_ENRICHMENT_CONCURRENCY,
                beforeBatch = { ensureCurrentNetworkScope(networkScope) }
            ) { ip ->
                val existingByIp = devicesCache.values.firstOrNull {
                    it.ip == ip && it.networkScope == networkScope
                }
                val mac = arpDevices[ip].orEmpty().ifBlank { existingByIp?.mac.orEmpty() }
                deviceEnrichmentEngine.enrich(
                    ip = ip,
                    mac = mac,
                    bssid = bssid,
                    networkScope = networkScope,
                    legacyNetworkScope = legacyNetworkScope,
                    localIp = localIp,
                    gateway = gateway,
                    hasTcpResponse = ip in discovery.tcpResponsiveIps,
                    hasNsdServiceObservation = ip in discovery.nsdServiceIps,
                    hasSsdpObservation = ip in discovery.ssdpServiceIps,
                    hasHostReachabilityProbe = ip in discovery.hostReachabilityIps,
                    existing = existingByIp,
                    existingByStableId = devicesCache[
                        if (mac.isNotBlank()) "${mac.uppercase()}@${networkScope}" else "ip:${networkScope}:$ip"
                    ],
                    now = now
                )
            }

            val result = deviceLifecycleEngine.reconcile(
                discoveredOnline = onlineDevices,
                cachedDevices = devicesCache.values,
                networkScope = networkScope,
                now = now
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

    private fun readArpTable(interfaceName: String): ArpTableReadResult {
        if (interfaceName.isBlank()) {
            AppLog.warning(AppLog.Component.NETWORK_DISCOVERY, AppLog.Event.ARP_TABLE_READ_FAILED)
            return ArpTableReadResult(entries = emptyMap(), available = false)
        }
        return try {
            val entries = BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                parseArpTable(reader.readText(), interfaceName)
            }
            ArpTableReadResult(entries = entries, available = true)
        } catch (_: Exception) {
            AppLog.warning(AppLog.Component.NETWORK_DISCOVERY, AppLog.Event.ARP_TABLE_READ_FAILED)
            ArpTableReadResult(entries = emptyMap(), available = false)
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
        val updated = updateDevicePorts(currentDevice.stableId, expectedScope, currentDevice.ip, ports)
            ?: return@withContext null
        ports
    }

    private suspend fun updateDevicePorts(
        stableId: String,
        expectedScope: String,
        expectedIp: String,
        ports: List<Int>
    ): Device? {
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
        return updated.takeIf { committed }
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
