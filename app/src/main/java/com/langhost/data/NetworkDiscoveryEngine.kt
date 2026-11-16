package com.langhost.data

import com.langhost.data.model.ScanCoverage
import com.langhost.data.network.Ipv4Subnet

/**
 * Platform-independent subnet discovery orchestration.
 *
 * ARP entries are supplied by the platform layer, while candidate generation,
 * bounded probing, evidence merging and coverage accounting stay deterministic
 * and independently testable.
 */
internal class NetworkDiscoveryEngine(
    private val probeHost: suspend (ip: String, budgetMs: Int) -> Boolean,
    private val ensureCurrentNetworkScope: suspend (expectedScope: String) -> Unit
) {
    suspend fun discover(
        localIp: String,
        gateway: String,
        netmask: String,
        arpEntries: Map<String, String>,
        networkScope: String,
        timeoutMs: Int,
        maxTargets: Int,
        maxConcurrency: Int
    ): NetworkDiscoverySnapshot {
        require(maxTargets >= 0) { "maxTargets must not be negative" }
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }

        val subnet = runCatching {
            Ipv4Subnet.fromAddressAndNetmask(localIp, netmask)
        }.getOrNull()

        // ARP tables can contain entries for other interfaces and subnets.
        val scopedArpEntries = subnet?.let { currentSubnet ->
            arpEntries.filterKeys(currentSubnet::contains)
        }.orEmpty()

        val candidates = subnet?.hostAddresses(maxTargets, localIp).orEmpty()
        val discoveredIps = HostDiscovery.discover(
            candidates = candidates,
            localIp = localIp,
            maxConcurrency = maxConcurrency,
            timeoutMs = timeoutMs,
            beforeBatch = { ensureCurrentNetworkScope(networkScope) },
            probe = probeHost
        )
        val endpoints = (scopedArpEntries.keys + discoveredIps + setOf(localIp, gateway).filter {
            it != "0.0.0.0" && it != "—" && it.isNotBlank()
        }).toCollection(LinkedHashSet())

        val coverage = ScanCoverage(
            subnetCidr = subnet?.let { "${it.networkAddressString()}/${it.prefixLength}" }.orEmpty(),
            checkedAddresses = candidates.size,
            totalUsableAddresses = subnet?.usableHostCount ?: 0L,
            truncated = subnet != null && candidates.size.toLong() < subnet.usableHostCount,
            networkScope = networkScope
        )

        return NetworkDiscoverySnapshot(
            ips = endpoints,
            arpAddresses = scopedArpEntries,
            coverage = coverage
        )
    }
}

internal data class NetworkDiscoverySnapshot(
    val ips: Set<String>,
    val arpAddresses: Map<String, String>,
    val coverage: ScanCoverage
)
