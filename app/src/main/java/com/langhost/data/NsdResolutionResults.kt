package com.langhost.data

import com.langhost.data.model.ProtocolEvidence

/**
 * Keeps DNS-SD resolutions and metadata attached to the advertisement that
 * produced them, so a lost service cannot leave stale endpoint evidence.
 */
internal class NsdResolutionResults {
    private data class Entry(
        val generation: Long,
        val addresses: Set<String>,
        val evidence: ProtocolEvidence
    )

    private val entriesByService = LinkedHashMap<String, Entry>()
    private val generationByService = HashMap<String, Long>()
    private val observedServices = LinkedHashSet<String>()
    private var resolutionFailures = 0

    /** Starts a new advertisement generation so late callbacks from an older one are ignored. */
    @Synchronized
    fun serviceObserved(serviceKey: String, evidence: ProtocolEvidence): Long {
        val generation = (generationByService[serviceKey] ?: 0L) + 1L
        generationByService[serviceKey] = generation
        observedServices.add(serviceKey)
        entriesByService[serviceKey] = Entry(generation, emptySet(), evidence)
        return generation
    }

    @Synchronized
    fun resolved(serviceKey: String, generation: Long, addresses: Set<String>) {
        val current = entriesByService[serviceKey] ?: return
        if (current.generation != generation) return
        entriesByService[serviceKey] = current.copy(addresses = addresses)
    }

    @Synchronized
    fun resolutionFailed() {
        resolutionFailures++
    }

    @Synchronized
    fun serviceLost(serviceKey: String, generation: Long? = null) {
        val current = entriesByService[serviceKey] ?: return
        if (generation != null && current.generation != generation) return
        entriesByService.remove(serviceKey)
        observedServices.remove(serviceKey)
    }

    @Synchronized
    fun addresses(): Set<String> = entriesByService.values.flatMap { it.addresses }.toSet()

    @Synchronized
    fun protocolEvidenceByIp(): Map<String, List<ProtocolEvidence>> =
        entriesByService.values.flatMap { entry ->
            entry.addresses.map { address -> address to entry.evidence }
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, evidence) -> evidence.distinct().take(MAX_EVIDENCE_PER_IP) }

    @Synchronized
    fun hasResolutionFailures(): Boolean = resolutionFailures > 0

    @Synchronized
    fun hasObservedServices(): Boolean = observedServices.isNotEmpty()

    private companion object {
        const val MAX_EVIDENCE_PER_IP = 16
    }
}
