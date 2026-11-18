package com.langhost.data

import com.langhost.data.model.PortAuditResult
import com.langhost.data.model.PortAuditStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
    private val discoveryProbeSemaphore = Semaphore(DISCOVERY_PORT_CONCURRENCY)

    suspend fun probeHost(
        ip: String,
        budgetMs: Int,
        portsToTry: IntArray = DISCOVERY_PORTS
    ): Boolean = withContext(Dispatchers.IO) {
        val boundedBudgetMs = budgetMs.coerceIn(100, 1_000)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(boundedBudgetMs.toLong())

        // Probe candidate services concurrently: a silently filtered first port must not
        // consume the time budget before the remaining ports are attempted.
        val positiveEvidence = AtomicBoolean(false)
        val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
        coroutineScope {
            portsToTry.distinct().map { port ->
                async(Dispatchers.IO) {
                    discoveryProbeSemaphore.withPermit {
                        currentCoroutineContext().ensureActive()
                        if (positiveEvidence.get()) return@withPermit false
                        val remainingNanos = deadline - System.nanoTime()
                        if (remainingNanos <= 0L) return@withPermit false
                        val remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)
                        val connectTimeout = remainingMs.coerceAtMost(MAX_DISCOVERY_CONNECT_TIMEOUT_MS).toInt()
                        val socket = Socket()
                        activeSockets.add(socket)
                        var responded = false
                        try {
                            // A sibling may have produced positive evidence while this task was queued.
                            if (positiveEvidence.get()) return@withPermit false
                            socket.connect(InetSocketAddress(ip, port), connectTimeout)
                            responded = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: ConnectException) {
                            // Only an explicit refusal proves the remote TCP stack responded.
                            // ConnectException can also represent routing/connectivity failures.
                            responded = isConnectionRefused(error)
                        } catch (_: Exception) {
                            // Timeouts and routing failures remain unknown.
                        } finally {
                            activeSockets.remove(socket)
                            runCatching { socket.close() }
                        }

                        if (responded && positiveEvidence.compareAndSet(false, true)) {
                            // Stop slower sibling connects as soon as any port proves the host responded.
                            activeSockets.toList().forEach { sibling ->
                                runCatching { sibling.close() }
                            }
                        }
                        responded
                    }
                }
            }.awaitAll().any { it } || positiveEvidence.get()
        }
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
        const val DISCOVERY_PORT_CONCURRENCY = 192
        const val MAX_DISCOVERY_CONNECT_TIMEOUT_MS = 400L
    }
}


/** A refusal (ECONNREFUSED/TCP RST) is positive evidence; generic connect failures are not. */
internal fun isConnectionRefused(error: ConnectException): Boolean {
    val message = error.message?.lowercase().orEmpty()
    return "econnrefused" in message || "connection refused" in message
}
