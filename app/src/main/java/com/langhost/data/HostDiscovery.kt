package com.langhost.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Bounded host discovery orchestration.
 *
 * The probe must return true only when it has positive evidence that the host responded.
 * A failed probe is treated as unknown/unconfirmed, not proof that the host is offline.
 */
internal object HostDiscovery {
    /**
     * Maps items in bounded batches, preserving input order and propagating cancellation.
     */
    suspend fun <T, R> mapBounded(
        items: List<T>,
        maxConcurrency: Int,
        beforeBatch: suspend () -> Unit = {},
        transform: suspend (T) -> R
    ): List<R> {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
        if (items.isEmpty()) return emptyList()
        val semaphore = Semaphore(maxConcurrency)
        return coroutineScope {
            items.map { item ->
                async {
                    semaphore.withPermit {
                        beforeBatch()
                        transform(item)
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun discover(
        candidates: List<String>,
        localIp: String,
        maxConcurrency: Int,
        timeoutMs: Int,
        beforeBatch: suspend () -> Unit = {},
        probe: suspend (ip: String, budgetMs: Int) -> Boolean
    ): Set<String> {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
        val budget = timeoutMs.coerceIn(100, 500) * 2
        val uniqueCandidates = candidates.asSequence().filter { it.isNotBlank() }.distinct().toList()
        val semaphore = Semaphore(maxConcurrency)

        suspend fun runPass(targets: List<String>, passBudget: Int): Set<String> = coroutineScope {
            targets.map { ip ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        beforeBatch()
                        if (ip == localIp) return@withPermit ip
                        try {
                            if (probe(ip, passBudget)) ip else null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull().toSet()
        }

        val firstPass = runPass(uniqueCandidates, budget)
        val retryCandidates = uniqueCandidates.filter { it != localIp && it !in firstPass }
        if (retryCandidates.isEmpty()) return firstPass
        // A short second attempt gives sleeping Wi-Fi clients another chance without
        // doubling the full per-host timeout budget.
        val secondPass = runPass(retryCandidates, budget.coerceAtMost(500))
        return firstPass + secondPass
    }
}
}
