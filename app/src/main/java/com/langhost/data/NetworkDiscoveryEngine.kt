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
        discoverSsdpServiceIps: suspend () -> Set<String> = { emptySet() },
        discoverHostReachabilityIps: suspend (List<String>) -> Set<String> = { emptySet() }
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
        val supplemental = coroutineScope {
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
            val reachabilityDiscovery = async {
                try {
                    val unresolved = candidates.filter { it != localIp && it !in discoveredIps }
                    if (subnet != null && unresolved.isNotEmpty()) {
                        discoverHostReachabilityIps(unresolved)
                    } else emptySet()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The platform reachability API is best-effort and may be unavailable.
                    emptySet()
                }
            }
            SupplementalDiscoveryResults(
                arpEntries = arpRefresh.await(),
                nsdIps = nsdDiscovery.await(),
                ssdpIps = ssdpDiscovery.await(),
                reachabilityIps = reachabilityDiscovery.await()
            )
        }
        ensureCurrentNetworkScope(networkScope)
        val scopedNsdServiceIps = subnet?.let { currentSubnet ->
            nsdServiceIps.asSequence().filter(currentSubnet::contains).take(MAX_NSD_HOSTS).toSet()
        }.orEmpty()
        val scopedSsdpServiceIps = subnet?.let { currentSubnet ->
            supplemental.ssdpIps.asSequence().filter(currentSubnet::contains).take(MAX_SSDP_HOSTS).toSet()
        }.orEmpty()
        val scopedReachabilityIps = subnet?.let { currentSubnet ->
            supplemental.reachabilityIps.asSequence().filter(currentSubnet::contains)
                .take(MAX_REACHABILITY_HOSTS).toSet()
        }.orEmpty()
        val allArpEntries = (scopedArpEntries + (subnet?.let { currentSubnet ->
            supplemental.arpEntries.filterKeys(currentSubnet::contains)
        }.orEmpty()))
        val endpoints = (allArpEntries.keys + discoveredIps + scopedNsdServiceIps + scopedSsdpServiceIps + scopedReachabilityIps + setOf(localIp, gateway).filter {
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
            ssdpResponsiveHosts = scopedSsdpServiceIps.size,
            reachabilityResponsiveHosts = scopedReachabilityIps.size
        )

        return NetworkDiscoverySnapshot(
            ips = endpoints,
            // HostDiscovery includes the local address without probing it; it is not a TCP response.
            tcpResponsiveIps = discoveredIps - localIp,
            arpAddresses = allArpEntries,
            nsdServiceIps = scopedNsdServiceIps,
            ssdpServiceIps = scopedSsdpServiceIps,
            hostReachabilityIps = scopedReachabilityIps,
            coverage = coverage
        )
    }
}

private const val MAX_NSD_HOSTS = 256
private const val MAX_SSDP_HOSTS = 256
private const val MAX_REACHABILITY_HOSTS = 1_024

private data class SupplementalDiscoveryResults(
    val arpEntries: Map<String, String>,
    val nsdIps: Set<String>,
    val ssdpIps: Set<String>,
    val reachabilityIps: Set<String>
)

internal data class NetworkDiscoverySnapshot(
    val ips: Set<String>,
    /** Hosts with a positive TCP connection response during this scan. */
    val tcpResponsiveIps: Set<String>,
    val arpAddresses: Map<String, String>,
    /** Hosts that resolved a supported DNS-SD service during this scan. */
    val nsdServiceIps: Set<String>,
    /** Hosts that returned a valid SSDP/UPnP discovery response. */
    val ssdpServiceIps: Set<String>,
    /** Hosts confirmed by the interface-bound best-effort reachability probe. */
    val hostReachabilityIps: Set<String>,
    val coverage: ScanCoverage
)
