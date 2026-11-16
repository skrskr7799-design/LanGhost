package com.langhost.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
import java.util.concurrent.atomic.AtomicInteger

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

    suspend fun discover(): Set<String> = withContext(Dispatchers.IO) {
        val addresses = ConcurrentHashMap.newKeySet<String>()
        val seenServices = ConcurrentHashMap.newKeySet<String>()
        val pendingResolutions = AtomicInteger(0)
        val multicastLock = acquireMulticastLockIfRequired()

        try {
            coroutineScope {
                SERVICE_TYPES.map { serviceType ->
                    async {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            discoverServiceTypeModern(serviceType, addresses)
                        } else {
                            discoverServiceTypeLegacy(
                                serviceType = serviceType,
                                addresses = addresses,
                                seenServices = seenServices,
                                pendingResolutions = pendingResolutions
                            )
                        }
                    }
                }.awaitAll()
            }
            addresses.toSet()
        } finally {
            multicastLock?.let { lock ->
                if (lock.isHeld) runCatching { lock.release() }
            }
        }
    }

    /**
     * API 34+ combines service discovery and resolution in one callback. It also
     * exposes all resolved host addresses instead of the legacy single-host field.
     */
    @SuppressLint("NewApi")
    private suspend fun discoverServiceTypeModern(
        serviceType: String,
        addresses: MutableSet<String>
    ) {
        val unregistered = CompletableDeferred<Unit>()
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                addResolvedAddresses(serviceInfo.hostAddresses, addresses)
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit

            override fun onServiceInfoCallbackUnregistered() {
                unregistered.complete(Unit)
            }

            override fun onServiceLost() = Unit
        }

        try {
            val request = NsdManager.DiscoveryRequest.Builder(serviceType).build()
            nsdManager.registerServiceInfoCallback(request, DIRECT_EXECUTOR, callback)
            delay(DISCOVERY_WINDOW_MS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // NSD may be disabled or the service type may be unavailable on this network.
        } finally {
            runCatching {
                nsdManager.unregisterServiceInfoCallback(callback)
                withTimeoutOrNull(STOP_TIMEOUT_MS) { unregistered.await() }
            }
        }
    }

    private suspend fun discoverServiceTypeLegacy(
        serviceType: String,
        addresses: MutableSet<String>,
        seenServices: MutableSet<String>,
        pendingResolutions: AtomicInteger
    ) {
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        lateinit var listener: NsdManager.DiscoveryListener

        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                started.complete(Unit)
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                val key = "${service.serviceName}|${service.serviceType}"
                if (!seenServices.add(key)) return
                if (pendingResolutions.incrementAndGet() > MAX_PENDING_RESOLUTIONS) {
                    pendingResolutions.decrementAndGet()
                    return
                }

                try {
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            pendingResolutions.decrementAndGet()
                        }

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            pendingResolutions.decrementAndGet()
                            @Suppress("DEPRECATION")
                            addResolvedAddresses(listOfNotNull(resolved.host), addresses)
                        }
                    })
                } catch (_: Exception) {
                    pendingResolutions.decrementAndGet()
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) {
                stopped.complete(Unit)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                started.completeExceptionally(IllegalStateException("NSD discovery could not start"))
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopped.complete(Unit)
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            withTimeoutOrNull(START_TIMEOUT_MS + DISCOVERY_WINDOW_MS + RESOLUTION_GRACE_MS) {
                started.await()
                delay(DISCOVERY_WINDOW_MS)
                // Give service-resolution callbacks a short bounded opportunity to finish.
                delay(RESOLUTION_GRACE_MS)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A service type may be unsupported or unavailable on this network.
        } finally {
            runCatching {
                nsdManager.stopServiceDiscovery(listener)
                withTimeoutOrNull(STOP_TIMEOUT_MS) { stopped.await() }
            }
        }
    }

    private fun addResolvedAddresses(
        hosts: Collection<InetAddress>,
        addresses: MutableSet<String>
    ) {
        hosts.filterIsInstance<Inet4Address>().forEach { host ->
            if (
                !host.isAnyLocalAddress &&
                !host.isLoopbackAddress &&
                !host.isLinkLocalAddress &&
                !host.isMulticastAddress
            ) {
                host.hostAddress?.let(addresses::add)
            }
        }
    }

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
    }
}
