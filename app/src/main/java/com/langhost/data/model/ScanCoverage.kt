package com.langhost.data.model

data class ScanCoverage(
    val subnetCidr: String = "",
    val checkedAddresses: Int = 0,
    val totalUsableAddresses: Long = 0L,
    val truncated: Boolean = false,
    val scannedAt: Long = 0L,
    /** Logical network identity for which this coverage was measured. */
    val networkScope: String = "",
    /** Distinct in-subnet hosts observed in the kernel ARP table after the scan. */
    val arpObservedHosts: Int = 0,
    /** Hosts that positively answered a TCP connection attempt (open or refused). */
    val tcpResponsiveHosts: Int = 0,
    /** Hosts resolved through one of the supported DNS-SD service types. */
    val dnsSdResolvedHosts: Int = 0,
    /** Hosts that returned a valid SSDP/UPnP discovery response. */
    val ssdpResponsiveHosts: Int = 0,
    /** Hosts confirmed by the best-effort interface-bound IP reachability probe. */
    val reachabilityResponsiveHosts: Int = 0,
    /** Whether /proc/net/arp could be read during this scan; null means not checked. */
    val arpAvailable: Boolean? = null,
    val arpStatus: DiscoverySourceStatus = DiscoverySourceStatus.NOT_RUN,
    val tcpStatus: DiscoverySourceStatus = DiscoverySourceStatus.NOT_RUN,
    val dnsSdStatus: DiscoverySourceStatus = DiscoverySourceStatus.NOT_REPORTED,
    val ssdpStatus: DiscoverySourceStatus = DiscoverySourceStatus.NOT_REPORTED,
    val reachabilityStatus: DiscoverySourceStatus = DiscoverySourceStatus.NOT_REPORTED
) {
    val coveragePercent: Int
        get() = if (totalUsableAddresses <= 0L) 0 else
            ((checkedAddresses.toLong() * 100L) / totalUsableAddresses).toInt().coerceIn(0, 100)
}
