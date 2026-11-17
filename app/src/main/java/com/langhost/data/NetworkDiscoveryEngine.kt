package com.langhost.data

import com.langhost.data.model.ScanCoverage
import com.langhost.data.model.DiscoverySourceStatus
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
        discoverNsdServiceIps: suspend () -> DiscoverySourceResult<Set<String>> = { DiscoverySourceResult(emptySet(), DiscoverySourceStatus.NOT_RUN) },
        discoverSsdpServiceIps: suspend () -> DiscoverySourceResult<Set<String>> = { DiscoverySourceResult(emptySet(), DiscoverySourceStatus.NOT_RUN) },
        discoverHostReachabilityIps: suspend (List<String>) -> DiscoverySourceResult<Set<String>> = { DiscoverySourceResult(emptySet(), DiscoverySourceStatus.NOT_RUN) }
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
                    if (subnet != null) discoverNsdServiceIps() else DiscoverySourceResult(emptySet(), DiscoverySourceStatus.NOT_RUN)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Optional DNS-SD must not discard successful ARP/TCP evidence.
                    DiscoverySourceResult(emptySet(), DiscoverySourceStatus.UNAVAILABLE)
                }
            }
            val ssdpDiscovery = async {
                try {
                    if (subnet != null) discoverSsdpServiceIps() else DiscoverySourceResult(emptySet(), DiscoverySourceStatus.NOT_RUN)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Optional SSDP must not discard successful ARP/TCP/DNS-SD evidence.
                    DiscoverySourceResult(emptySet(), DiscoverySourceStatus.UNAVAILABLE)
                }
            }
            val reachabilityDiscovery = async {
                try {
                    val unresolved = candidates.filter { it != localIp && it !in discoveredIps }
                    if (subnet != null && unresolved.isNotEmpty()) {
                        discoverHostReachabilityIps(unresolved)
                    } else DiscoverySourceResult(emptySet(), DiscoverySourceStatus.NOT_RUN)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The platform reachability API is best-effort and may be unavailable.
                    DiscoverySourceResult(emptySet(), DiscoverySourceStatus.UNAVAILABLE)
                }
            }
            SupplementalDiscoveryResults(
                arpEntries = arpRefresh.await(),
                nsd = nsdDiscovery.await(),
                ssdp = ssdpDiscovery.await(),
                reachability = reachabilityDiscovery.await()
            )
        }
        ensureCurrentNetworkScope(networkScope)
        val scopedNsdServiceIps = subnet?.let { currentSubnet ->
            supplemental.nsd.value.asSequence().filter(currentSubnet::contains).take(MAX_NSD_HOSTS).toSet()
        }.orEmpty()
        val scopedSsdpServiceIps = subnet?.let { currentSubnet ->
            supplemental.ssdp.value.asSequence().filter(currentSubnet::contains).take(MAX_SSDP_HOSTS).toSet()
        }.orEmpty()
        val scopedReachabilityIps = subnet?.let { currentSubnet ->
            supplemental.reachability.value.asSequence().filter(currentSubnet::contains)
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
            reachabilityResponsiveHosts = scopedReachabilityIps.size,
            dnsSdStatus = supplemental.nsd.status,
            ssdpStatus = supplemental.ssdp.status,
            reachabilityStatus = supplemental.reachability.status,
            tcpStatus = when {
                subnet == null || candidates.isEmpty() -> DiscoverySourceStatus.NOT_RUN
                (discoveredIps - localIp).isNotEmpty() -> DiscoverySourceStatus.RESPONDED
                else -> DiscoverySourceStatus.NO_RESPONSES
            }
        )

        return NetworkDiscoverySnapshot(
            ips = endpoints,
            // HostDiscovery includes the local address without probing it; it is not a TCP response.
            tcpResponsiveIps = discoveredIps - localIp,
            arpAddresses = allArpEntries,
            nsdServiceIps = scopedNsdServiceIps,
            ssdpServiceIps = scopedSsdpServiceIps,
            hostReachabilityIps = scopedReachabilityIps,
            coverage = coverage,
            protocolEvidenceByIp = (supplemental.nsd.protocolEvidenceByIp + supplemental.ssdp.protocolEvidenceByIp)
                .mapValues { (_, evidence) -> evidence.distinct().take(MAX_PROTOCOL_EVIDENCE_PER_HOST) }
        )
    }
}

private const val MAX_NSD_HOSTS = 256
private const val MAX_SSDP_HOSTS = 256
private const val MAX_REACHABILITY_HOSTS = 1_024
private const val MAX_PROTOCOL_EVIDENCE_PER_HOST = 24

private data class SupplementalDiscoveryResults(
    val arpEntries: Map<String, String>,
    val nsd: DiscoverySourceResult<Set<String>>,
    val ssdp: DiscoverySourceResult<Set<String>>,
    val reachability: DiscoverySourceResult<Set<String>>
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
    val coverage: ScanCoverage,
    /** Sanitized DNS-SD/SSDP metadata keyed by an in-subnet IPv4 endpoint. */
    val protocolEvidenceByIp: Map<String, List<com.langhost.data.model.ProtocolEvidence>> = emptyMap()
)
