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
    private var nextGeneration = 0L
    private var resolutionFailures = 0
    private var trackingLimitReached = false

    /** Starts a new advertisement generation so late callbacks from an older one are ignored. */
    @Synchronized
    fun serviceObserved(serviceKey: String, evidence: ProtocolEvidence): Long? {
        if (entriesByService.containsKey(serviceKey)) return null
        if (entriesByService.size >= MAX_TRACKED_SERVICES) {
            trackingLimitReached = true
            return null
        }
        nextGeneration += 1L
        val generation = nextGeneration
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
    fun serviceLost(serviceKey: String, generation: Long) {
        val current = entriesByService[serviceKey] ?: return
        if (current.generation != generation) return
        entriesByService.remove(serviceKey)
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
    fun hasObservedServices(): Boolean = entriesByService.isNotEmpty()

    @Synchronized
    fun trackedServiceCount(): Int = entriesByService.size

    @Synchronized
    fun hasTrackingLimitReached(): Boolean = trackingLimitReached

    internal companion object {
        const val MAX_EVIDENCE_PER_IP = 16
        const val MAX_TRACKED_SERVICES = 256
    }
}
