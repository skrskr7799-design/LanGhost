package com.langhost.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.onAwait
import kotlinx.coroutines.selects.select

/**
 * Bounded host discovery orchestration.
 *
 * The probe must return true only when it has positive evidence that the host responded.
 * A failed probe is treated as unknown/unconfirmed, not proof that the host is offline.
 */
internal object HostDiscovery {
    /**
     * Maps items through a sliding window. A completed task immediately frees a slot
     * for the next item; the scheduler never waits for a whole fixed batch to finish.
     */
    suspend fun <T, R> mapBounded(
        items: List<T>,
        maxConcurrency: Int,
        beforeBatch: suspend () -> Unit = {},
        transform: suspend (T) -> R
    ): List<R> {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
        if (items.isEmpty()) return emptyList()

        return coroutineScope {
            val results = arrayOfNulls<Any?>(items.size)
            val inFlight = mutableListOf<Deferred<Pair<Int, R>>>()
            var nextIndex = 0
            var scheduled = 0

            while (nextIndex < items.size || inFlight.isNotEmpty()) {
                while (nextIndex < items.size && inFlight.size < maxConcurrency) {
                    if (scheduled % maxConcurrency == 0) beforeBatch()
                    val index = nextIndex++
                    inFlight += async { index to transform(items[index]) }
                    scheduled++
                }

                if (inFlight.isNotEmpty()) {
                    val completed = select<Pair<Deferred<Pair<Int, R>>, Pair<Int, R>>> {
                        inFlight.forEach { deferred ->
                            deferred.onAwait { value -> deferred to value }
                        }
                    }
                    inFlight.remove(completed.first)
                    results[completed.second.first] = completed.second.second
                }
            }

            @Suppress("UNCHECKED_CAST")
            results.map { it as R }
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

        suspend fun runPass(targets: List<String>, passBudget: Int): Set<String> =
            mapBounded(
                items = targets,
                maxConcurrency = maxConcurrency,
                beforeBatch = beforeBatch
            ) { ip ->
                if (ip == localIp) {
                    ip
                } else {
                    try {
                        if (probe(ip, passBudget)) ip else null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }
            }.filterNotNull().toSet()

        val firstPass = runPass(uniqueCandidates, budget)
        val retryCandidates = uniqueCandidates.filter { it != localIp && it !in firstPass }
        if (retryCandidates.isEmpty()) return firstPass
        // A short second attempt gives sleeping Wi-Fi clients another chance without
        // doubling the full per-host timeout budget.
        val secondPass = runPass(retryCandidates, budget.coerceAtMost(500))
        return firstPass + secondPass
    }
}
