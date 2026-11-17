package com.langhost.data

import com.langhost.data.model.DiscoverySourceStatus
import com.langhost.data.model.ProtocolEvidence

/** Aggregates valid SSDP responders and sanitized metadata, preserving mid-window failures. */
internal class SsdpDiscoveryResults {
    private val evidenceByAddress = LinkedHashMap<String, MutableSet<ProtocolEvidence>>()
    private var operationFailed = false

    @Synchronized
    fun addResponder(address: String, evidence: ProtocolEvidence) {
        evidenceByAddress.getOrPut(address) { LinkedHashSet() }.add(evidence)
    }

    @Synchronized
    fun markOperationFailed() {
        operationFailed = true
    }

    @Synchronized
    fun snapshot(): Set<String> = evidenceByAddress.keys.toSet()

    @Synchronized
    fun protocolEvidenceByIp(): Map<String, List<ProtocolEvidence>> =
        evidenceByAddress.mapValues { (_, evidence) -> evidence.take(MAX_EVIDENCE_PER_IP) }

    @Synchronized
    fun status(): DiscoverySourceStatus = when {
        operationFailed && evidenceByAddress.isNotEmpty() -> DiscoverySourceStatus.PARTIAL
        operationFailed -> DiscoverySourceStatus.UNAVAILABLE
        evidenceByAddress.isNotEmpty() -> DiscoverySourceStatus.RESPONDED
        else -> DiscoverySourceStatus.NO_RESPONSES
    }

    private companion object {
        const val MAX_EVIDENCE_PER_IP = 16
    }
}
