package com.langhost.data

import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface

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
    ): Set<String> = withContext(Dispatchers.IO) {
        if (activeNetwork == null || interfaceName.isBlank() || candidateIps.isEmpty()) {
            return@withContext emptySet()
        }
        val networkInterface = try {
            NetworkInterface.getByName(interfaceName)
        } catch (_: Exception) {
            null
        } ?: return@withContext emptySet()

        try {
            HostDiscovery.mapBounded(
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
                    null
                }
            }.filterNotNull().toSet()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptySet()
        }
    }

    private companion object {
        const val HOP_LIMIT = 1
        const val MIN_TIMEOUT_MS = 100
        const val MAX_TIMEOUT_MS = 400
    }
}
