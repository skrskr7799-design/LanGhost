package com.langhost.data

import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicInteger
import com.langhost.data.model.DiscoverySourceStatus

/**
 * Best-effort IP reachability fallback for hosts that did not answer TCP.
 *
 * InetAddress.isReachable may use ICMP echo or a platform TCP-echo fallback.
 * A negative result is never treated as proof that a host is offline.
 */
internal class AndroidIcmpDiscovery {
    suspend fun discover(
        candidateIps: List<String>,
        interfaceName: String,
        activeNetwork: Network?,
        maxConcurrency: Int,
        timeoutMs: Int,
        beforeWindow: suspend () -> Unit
    ): DiscoverySourceResult<Set<String>> = withContext(Dispatchers.IO) {
        if (candidateIps.isEmpty()) {
            return@withContext DiscoverySourceResult(emptySet(), DiscoverySourceStatus.NOT_RUN)
        }
        if (activeNetwork == null || interfaceName.isBlank()) {
            return@withContext DiscoverySourceResult(emptySet(), DiscoverySourceStatus.UNAVAILABLE)
        }
        val networkInterface = try {
            NetworkInterface.getByName(interfaceName)
        } catch (_: Exception) {
            null
        } ?: return@withContext DiscoverySourceResult(emptySet(), DiscoverySourceStatus.UNAVAILABLE)

        val failedProbes = AtomicInteger(0)
        try {
            val reachable = HostDiscovery.mapBounded(
                items = candidateIps.distinct(),
                maxConcurrency = maxConcurrency,
                beforeBatch = beforeWindow
            ) { ip ->
                try {
                    InetAddress.getByName(ip).isReachable(
                        networkInterface,
                        HOP_LIMIT,
                        timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
                    ).takeIf { it }?.let { ip }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failedProbes.incrementAndGet()
                    null
                }
            }.filterNotNull().toSet()
            DiscoverySourceResult(
                value = reachable,
                status = when {
                    reachable.isNotEmpty() -> DiscoverySourceStatus.RESPONDED
                    failedProbes.get() > 0 -> DiscoverySourceStatus.UNAVAILABLE
                    else -> DiscoverySourceStatus.NO_RESPONSES
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DiscoverySourceResult(emptySet(), DiscoverySourceStatus.UNAVAILABLE)
        }
    }

    private companion object {
        const val HOP_LIMIT = 1
        const val MIN_TIMEOUT_MS = 100
        const val MAX_TIMEOUT_MS = 400
    }
}
