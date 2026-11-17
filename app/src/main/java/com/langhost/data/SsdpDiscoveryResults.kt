package com.langhost.data

import com.langhost.data.model.DiscoverySourceStatus

/** Aggregates valid SSDP responders and preserves failures that happen mid-window. */
internal class SsdpDiscoveryResults {
    private val responders = LinkedHashSet<String>()
    private var operationFailed = false

    @Synchronized
    fun addResponder(address: String) {
        responders.add(address)
    }

    @Synchronized
    fun markOperationFailed() {
        operationFailed = true
    }

    @Synchronized
    fun snapshot(): Set<String> = responders.toSet()

    @Synchronized
    fun status(): DiscoverySourceStatus = when {
        operationFailed && responders.isNotEmpty() -> DiscoverySourceStatus.PARTIAL
        operationFailed -> DiscoverySourceStatus.UNAVAILABLE
        responders.isNotEmpty() -> DiscoverySourceStatus.RESPONDED
        else -> DiscoverySourceStatus.NO_RESPONSES
    }
}
