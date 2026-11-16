package com.langhost.data

import com.langhost.data.model.PortAuditResult
import com.langhost.data.model.PortAuditStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Owns bounded TCP probing mechanics. Repository policy (active network identity and
 * scan configuration) is supplied through callbacks, keeping this engine independent
 * from Android Context and persistence.
 */
internal class NetworkProbeEngine(
    private val scanTimeoutMs: suspend () -> Int,
    private val ensureCurrentNetworkScope: suspend (String) -> Unit
) {
    private val portProbeSemaphore = Semaphore(PORT_SCAN_CONCURRENCY)

    suspend fun probeHost(
        ip: String,
        budgetMs: Int,
        portsToTry: IntArray = DISCOVERY_PORTS
    ): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs.coerceIn(100, 1_000).toLong())

        for (port in portsToTry) {
            currentCoroutineContext().ensureActive()
            val remainingNanos = deadline - System.nanoTime()
            if (remainingNanos <= 0L) break
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)
            val connectTimeout = remainingMs.coerceAtMost(200L).toInt()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), connectTimeout)
                    return@withContext true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ConnectException) {
                // A TCP RST/refusal is positive evidence that a host (or active endpoint) responded.
                return@withContext true
            } catch (_: Exception) {
                // Timeouts and routing failures are unknown, not proof that the host is offline.
            }
        }
        false
    }

    suspend fun scanPorts(
        ip: String,
        ports: List<Int>,
        expectedNetworkScope: String
    ): List<Int> = withContext(Dispatchers.IO) {
        val timeoutMs = (scanTimeoutMs() * 2).coerceIn(100, 800)
        val validPorts = ports.distinct().filter { it in 1..65535 }
        HostDiscovery.mapBounded(
            items = validPorts,
            maxConcurrency = PORT_SCAN_CONCURRENCY,
            beforeBatch = { ensureCurrentNetworkScope(expectedNetworkScope) }
        ) { port ->
            portProbeSemaphore.withPermit {
                currentCoroutineContext().ensureActive()
                ensureCurrentNetworkScope(expectedNetworkScope)
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, port), timeoutMs)
                        port
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }.filterNotNull().sorted()
    }

    /**
     * Records every attempted port, not just successful connections. Refused connections are
     * CLOSED; connect timeouts are FILTERED/NO RESPONSE; routing and other IO failures are UNKNOWN.
     */
    suspend fun scanSecurityPortEvidence(
        ip: String,
        ports: List<Int>,
        expectedNetworkScope: String
    ): List<PortAuditResult> = withContext(Dispatchers.IO) {
        val timeoutMs = (scanTimeoutMs() * 2).coerceIn(100, 800)
        val validPorts = ports.distinct().filter { it in 1..65535 }
        HostDiscovery.mapBounded(
            items = validPorts,
            maxConcurrency = PORT_SCAN_CONCURRENCY,
            beforeBatch = { ensureCurrentNetworkScope(expectedNetworkScope) }
        ) { port ->
            portProbeSemaphore.withPermit {
                currentCoroutineContext().ensureActive()
                ensureCurrentNetworkScope(expectedNetworkScope)
                val startedAt = System.nanoTime()
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, port), timeoutMs)
                    }
                    PortAuditResult(
                        port = port,
                        status = PortAuditStatus.OPEN,
                        latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    PortAuditResult.fromFailure(
                        port = port,
                        error = error,
                        latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    )
                }
            }
        }.sortedBy { it.port }
    }

    private companion object {
        val DISCOVERY_PORTS = intArrayOf(80, 443, 22, 445, 8080, 53)
        const val PORT_SCAN_CONCURRENCY = 16
    }
}
