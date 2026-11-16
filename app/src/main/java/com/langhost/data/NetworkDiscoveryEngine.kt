package com.langhost.data

import com.langhost.data.model.ScanCoverage
import com.langhost.data.network.Ipv4Subnet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
        maxConcurrency: Int,
        refreshArpEntries: suspend () -> Map<String, String> = { emptyMap() },
        discoverNsdServiceIps: suspend () -> Set<String> = { emptySet() },
        discoverSsdpServiceIps: suspend () -> Set<String> = { emptySet() }
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
        // TCP connection attempts also trigger kernel neighbor resolution for candidate IPs.
        // Re-read the active interface's ARP table afterwards so hosts that answer ARP but
        // expose none of the probed TCP services are not silently omitted from inventory.
        // Scope checks on both sides prevent evidence from a previous network being committed.
        ensureCurrentNetworkScope(networkScope)
        val (refreshedArpEntries, nsdServiceIps, ssdpServiceIps) = coroutineScope {
            val arpRefresh = async { refreshArpEntries() }
            val nsdDiscovery = async {
                try {
                    if (subnet != null) discoverNsdServiceIps() else emptySet()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Optional DNS-SD must not discard successful ARP/TCP evidence.
                    emptySet()
                }
            }
            val ssdpDiscovery = async {
                try {
                    if (subnet != null) discoverSsdpServiceIps() else emptySet()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Optional SSDP must not discard successful ARP/TCP/DNS-SD evidence.
                    emptySet()
                }
            }
            Triple(arpRefresh.await(), nsdDiscovery.await(), ssdpDiscovery.await())
        }
        ensureCurrentNetworkScope(networkScope)
        val scopedNsdServiceIps = subnet?.let { currentSubnet ->
            nsdServiceIps.asSequence().filter(currentSubnet::contains).take(MAX_NSD_HOSTS).toSet()
        }.orEmpty()
        val scopedSsdpServiceIps = subnet?.let { currentSubnet ->
            ssdpServiceIps.asSequence().filter(currentSubnet::contains).take(MAX_SSDP_HOSTS).toSet()
        }.orEmpty()
        val allArpEntries = (scopedArpEntries + (subnet?.let { currentSubnet ->
            refreshedArpEntries.filterKeys(currentSubnet::contains)
        }.orEmpty()))
        val endpoints = (allArpEntries.keys + discoveredIps + scopedNsdServiceIps + scopedSsdpServiceIps + setOf(localIp, gateway).filter {
            it != "0.0.0.0" && it != "—" && it.isNotBlank()
        }).toCollection(LinkedHashSet())

        val coverage = ScanCoverage(
            subnetCidr = subnet?.let { "${it.networkAddressString()}/${it.prefixLength}" }.orEmpty(),
            checkedAddresses = candidates.size,
            totalUsableAddresses = subnet?.usableHostCount ?: 0L,
            truncated = subnet != null && candidates.size.toLong() < subnet.usableHostCount,
            networkScope = networkScope,
            arpObservedHosts = allArpEntries.size,
            tcpResponsiveHosts = (discoveredIps - localIp).size,
            dnsSdResolvedHosts = scopedNsdServiceIps.size,
            ssdpResponsiveHosts = scopedSsdpServiceIps.size
        )

        return NetworkDiscoverySnapshot(
            ips = endpoints,
            // HostDiscovery includes the local address without probing it; it is not a TCP response.
            tcpResponsiveIps = discoveredIps - localIp,
            arpAddresses = allArpEntries,
            nsdServiceIps = scopedNsdServiceIps,
            ssdpServiceIps = scopedSsdpServiceIps,
            coverage = coverage
        )
    }
}

private const val MAX_NSD_HOSTS = 256
private const val MAX_SSDP_HOSTS = 256

internal data class NetworkDiscoverySnapshot(
    val ips: Set<String>,
    /** Hosts with a positive TCP connection response during this scan. */
    val tcpResponsiveIps: Set<String>,
    val arpAddresses: Map<String, String>,
    /** Hosts that resolved a supported DNS-SD service during this scan. */
    val nsdServiceIps: Set<String>,
    /** Hosts that returned a valid SSDP/UPnP discovery response. */
    val ssdpServiceIps: Set<String>,
    val coverage: ScanCoverage
)
