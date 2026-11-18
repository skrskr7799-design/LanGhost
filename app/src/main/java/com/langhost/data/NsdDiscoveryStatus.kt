package com.langhost.data

import com.langhost.data.model.DiscoverySourceStatus

/**
 * Converts per-service-type startup and aggregate resolution outcomes into
 * privacy-safe source coverage without treating a partial startup as complete.
 */
internal fun nsdDiscoveryStatus(
    startedTypes: List<Boolean>,
    resolvedAddresses: Set<String>,
    observedServices: Boolean,
    resolutionFailures: Boolean,
    trackingLimitReached: Boolean
): DiscoverySourceStatus = when {
    startedTypes.isEmpty() || startedTypes.none { it } -> DiscoverySourceStatus.UNAVAILABLE
    startedTypes.any { !it } || resolutionFailures || trackingLimitReached -> DiscoverySourceStatus.PARTIAL
    resolvedAddresses.isNotEmpty() -> DiscoverySourceStatus.RESPONDED
    observedServices -> DiscoverySourceStatus.PARTIAL
    else -> DiscoverySourceStatus.NO_RESPONSES
}
