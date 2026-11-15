package com.langhost.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Bounded host discovery orchestration.
 *
 * The probe must return true only when it has positive evidence that the host responded.
 * A failed probe is treated as unknown/unconfirmed, not proof that the host is offline.
 */
internal object HostDiscovery {
    suspend fun discover(
        candidates: List<String>,
        localIp: String,
        maxConcurrency: Int,
        timeoutMs: Int,
        probe: suspend (ip: String, budgetMs: Int) -> Boolean
    ): Set<String> {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
        val budget = timeoutMs.coerceIn(50, 500) * 2
        val uniqueCandidates = candidates.asSequence().filter { it.isNotBlank() }.distinct().toList()

        return coroutineScope {
            val alive = LinkedHashSet<String>()
            for (batch in uniqueCandidates.chunked(maxConcurrency)) {
                val results = batch.map { ip ->
                    async(Dispatchers.IO) {
                        if (ip == localIp) return@async ip
                        try {
                            if (probe(ip, budget)) ip else null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.awaitAll()
                alive.addAll(results.filterNotNull())
            }
            alive
        }
    }
}
