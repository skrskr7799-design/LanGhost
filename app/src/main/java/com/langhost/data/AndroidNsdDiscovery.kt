package com.langhost.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ext.SdkExtensions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.langhost.data.model.DiscoverySourceStatus

/**
 * Bounded foreground DNS-SD discovery.
 *
 * This is deliberately complementary to subnet probing and ARP: it only finds
 * hosts that advertise one of the supported service types over mDNS/DNS-SD.
 */
internal class AndroidNsdDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @SuppressLint("NewApi")
    suspend fun discover(activeNetwork: Network?): DiscoverySourceResult<Set<String>> = withContext(Dispatchers.IO) {
        val results = NsdResolutionResults()
        val seenServices = ConcurrentHashMap.newKeySet<String>()
        val activeInfoCallbacks = ConcurrentHashMap.newKeySet<NsdManager.ServiceInfoCallback>()
        val pendingResolutions = AtomicInteger(0)
        val multicastLock = acquireMulticastLockIfRequired()

        try {
            val startedTypes = coroutineScope {
                SERVICE_TYPES.map { serviceType ->
                    async {
                        discoverServiceType(
                            serviceType = serviceType,
                            activeNetwork = activeNetwork,
                            results = results,
                            seenServices = seenServices,
                            activeInfoCallbacks = activeInfoCallbacks,
                            pendingResolutions = pendingResolutions
                        )
                    }
                }.awaitAll()
            }
            val resolved = results.addresses()
            DiscoverySourceResult(
                value = resolved,
                status = when {
                    startedTypes.none { it } -> DiscoverySourceStatus.UNAVAILABLE
                    startedTypes.any { !it } || results.hasResolutionFailures() -> DiscoverySourceStatus.PARTIAL
                    resolved.isNotEmpty() -> DiscoverySourceStatus.RESPONDED
                    results.hasObservedServices() -> DiscoverySourceStatus.PARTIAL
                    else -> DiscoverySourceStatus.NO_RESPONSES
                }
            )
        } finally {
            // A cancelled scan must not leave service-info listeners registered.
            activeInfoCallbacks.toList().forEach { callback ->
                runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
            }
            withTimeoutOrNull(STOP_TIMEOUT_MS) {
                while (activeInfoCallbacks.isNotEmpty()) delay(10)
            }
            multicastLock?.let { lock ->
                if (lock.isHeld) runCatching { lock.release() }
            }
        }
    }

    @SuppressLint("NewApi")
    private suspend fun discoverServiceType(
        serviceType: String,
        activeNetwork: Network?,
        results: NsdResolutionResults,
        seenServices: MutableSet<String>,
        activeInfoCallbacks: MutableSet<NsdManager.ServiceInfoCallback>,
        pendingResolutions: AtomicInteger
    ): Boolean {
        val started = CompletableDeferred<Boolean>()
        val stopped = CompletableDeferred<Unit>()
        lateinit var listener: NsdManager.DiscoveryListener

        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                started.complete(true)
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                val key = "${service.serviceName}|${service.serviceType}"
                if (!seenServices.add(key)) return
                results.serviceObserved()
                if (pendingResolutions.incrementAndGet() > MAX_PENDING_RESOLUTIONS) {
                    pendingResolutions.decrementAndGet()
                    return
                }

                if (supportsServiceInfoCallbacks()) {
                    registerResolvedServiceCallback(service, key, results, activeInfoCallbacks, pendingResolutions)
                } else {
                    resolveServiceLegacy(service, key, results, pendingResolutions)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                results.serviceLost("${service.serviceName}|${service.serviceType}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                stopped.complete(Unit)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                started.complete(false)
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopped.complete(Unit)
            }
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activeNetwork != null) {
                nsdManager.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    activeNetwork,
                    DIRECT_EXECUTOR,
                    listener
                )
            } else {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }
            withTimeoutOrNull(START_TIMEOUT_MS + DISCOVERY_WINDOW_MS + RESOLUTION_GRACE_MS) {
                val didStart = started.await()
                if (didStart) {
                    delay(DISCOVERY_WINDOW_MS)
                    // Give service-resolution callbacks a short bounded opportunity to finish.
                    delay(RESOLUTION_GRACE_MS)
                }
                didStart
            } ?: false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A service type may be unsupported or unavailable on this network.
            false
        } finally {
            runCatching {
                nsdManager.stopServiceDiscovery(listener)
                withTimeoutOrNull(STOP_TIMEOUT_MS) { stopped.await() }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun registerResolvedServiceCallback(
        service: NsdServiceInfo,
        serviceKey: String,
        results: NsdResolutionResults,
        activeInfoCallbacks: MutableSet<NsdManager.ServiceInfoCallback>,
        pendingResolutions: AtomicInteger
    ) {
        val released = AtomicBoolean(false)
        lateinit var callback: NsdManager.ServiceInfoCallback

        fun release() {
            if (released.compareAndSet(false, true)) {
                activeInfoCallbacks.remove(callback)
                pendingResolutions.decrementAndGet()
            }
        }

        callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                results.resolved(serviceKey, resolvedAddresses(serviceInfo.hostAddresses))
                runCatching { nsdManager.unregisterServiceInfoCallback(this) }
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                results.resolutionFailed()
                release()
            }

            override fun onServiceInfoCallbackUnregistered() {
                release()
            }

            override fun onServiceLost() {
                results.serviceLost(serviceKey)
                runCatching { nsdManager.unregisterServiceInfoCallback(this) }
            }
        }

        activeInfoCallbacks.add(callback)
        try {
            nsdManager.registerServiceInfoCallback(service, DIRECT_EXECUTOR, callback)
        } catch (_: Exception) {
            results.resolutionFailed()
            release()
        }
    }

    private fun resolveServiceLegacy(
        service: NsdServiceInfo,
        serviceKey: String,
        results: NsdResolutionResults,
        pendingResolutions: AtomicInteger
    ) {
        try {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    results.resolutionFailed()
                    results.serviceLost(serviceKey)
                    pendingResolutions.decrementAndGet()
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    pendingResolutions.decrementAndGet()
                    @Suppress("DEPRECATION")
                    results.resolved(serviceKey, resolvedAddresses(listOfNotNull(resolved.host)))
                }
            })
        } catch (_: Exception) {
            results.resolutionFailed()
            pendingResolutions.decrementAndGet()
        }
    }

    private fun resolvedAddresses(
        hosts: Collection<InetAddress>
    ): Set<String> {
        return hosts.filterIsInstance<Inet4Address>().mapNotNull { host ->
            if (
                !host.isAnyLocalAddress &&
                !host.isLoopbackAddress &&
                !host.isLinkLocalAddress &&
                !host.isMulticastAddress
            ) {
                host.hostAddress
            } else null
        }.distinct().take(MAX_RESOLVED_HOSTS).toSet()
    }

    private fun supportsServiceInfoCallbacks(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 7

    private fun acquireMulticastLockIfRequired(): WifiManager.MulticastLock? {
        val needsLock = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) < 7)
        if (!needsLock) return null

        return runCatching {
            wifiManager.createMulticastLock("LanGhost-Nsd").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private companion object {
        // Common LAN service types; DNS-SD does not provide a universal host inventory.
        val SERVICE_TYPES = listOf(
            "_http._tcp.",
            "_https._tcp.",
            "_ssh._tcp.",
            "_smb._tcp.",
            "_googlecast._tcp.",
            "_airplay._tcp.",
            "_ipp._tcp."
        )
        val DIRECT_EXECUTOR = Executor { command -> command.run() }
        const val START_TIMEOUT_MS = 2_000L
        const val STOP_TIMEOUT_MS = 500L
        const val DISCOVERY_WINDOW_MS = 1_500L
        const val RESOLUTION_GRACE_MS = 300L
        const val MAX_PENDING_RESOLUTIONS = 4
        const val MAX_RESOLVED_HOSTS = 256
    }
}
