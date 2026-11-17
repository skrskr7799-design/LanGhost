package com.langhost.data

/**
 * Keeps DNS-SD resolutions attached to the service advertisement that produced
 * them. A lost advertisement can therefore be removed without retaining stale
 * host addresses from an earlier callback.
 */
internal class NsdResolutionResults {
    private val addressesByService = LinkedHashMap<String, Set<String>>()
    private var resolutionFailures = 0
    private var observedServices = 0

    @Synchronized
    fun serviceObserved() {
        observedServices++
    }

    @Synchronized
    fun resolved(serviceKey: String, addresses: Set<String>) {
        addressesByService[serviceKey] = addresses
    }

    @Synchronized
    fun resolutionFailed() {
        resolutionFailures++
    }

    @Synchronized
    fun serviceLost(serviceKey: String) {
        addressesByService.remove(serviceKey)
    }

    @Synchronized
    fun addresses(): Set<String> = addressesByService.values.flatten().toSet()

    @Synchronized
    fun hasResolutionFailures(): Boolean = resolutionFailures > 0

    @Synchronized
    fun hasObservedServices(): Boolean = observedServices > 0
}
