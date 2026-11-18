package com.langhost.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Bounded host discovery orchestration.
 *
 * The probe must return true only when it has positive evidence that the host responded.
 * A failed probe is treated as unknown/unconfirmed, not proof that the host is offline.
 */
internal object HostDiscovery {
    /**
     * Maps items through a sliding concurrency window. A completed task immediately frees
     * a slot for the next item; no fixed batch waits for its slowest member.
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
            val completed = Channel<Pair<Int, Result<R>>>(Channel.UNLIMITED)
            var nextIndex = 0
            var active = 0
            var scheduled = 0

            suspend fun scheduleNext() {
                if (nextIndex >= items.size) return
                if (scheduled % maxConcurrency == 0) beforeBatch()
                val index = nextIndex++
                active++
                scheduled++
                launch {
                    val outcome = try {
                        Result.success(transform(items[index]))
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                    completed.send(index to outcome)
                }
            }

            try {
                while (active < maxConcurrency && nextIndex < items.size) scheduleNext()
                while (active > 0) {
                    val (index, outcome) = completed.receive()
                    results[index] = outcome.getOrThrow()
                    active--
                    if (nextIndex < items.size) scheduleNext()
                }
            } finally {
                completed.close()
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
