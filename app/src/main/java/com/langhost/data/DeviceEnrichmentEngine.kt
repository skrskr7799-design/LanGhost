package com.langhost.data

import com.langhost.data.model.DeviceTypeClassifier
import com.langhost.data.model.Device
import com.langhost.data.model.DiscoveryEvidence
import com.langhost.data.model.ProtocolEvidence

/**
 * Builds a complete network-scoped device snapshot from discovery evidence and cached state.
 * Persistence, cache lookup and platform probing are injected by the repository.
 */
internal class DeviceEnrichmentEngine(
    private val loadCustomization: suspend (stableId: String, legacyIds: List<String>) -> DeviceCustomization,
    private val resolveHostname: suspend (ip: String) -> String,
    private val lookupVendor: (mac: String) -> String
) {
    suspend fun enrich(
        ip: String,
        mac: String,
        bssid: String,
        networkScope: String,
        legacyNetworkScope: String,
        localIp: String,
        gateway: String,
        hasTcpResponse: Boolean = false,
        hasNsdServiceObservation: Boolean = false,
        hasSsdpObservation: Boolean = false,
        hasHostReachabilityProbe: Boolean = false,
        protocolEvidence: List<ProtocolEvidence> = emptyList(),
        existing: Device?,
        existingByStableId: Device?,
        now: Long
    ): Device {
        val macFinal = mac
        val randomMac = Device.isRandomMacAddress(macFinal)
        val stableId = if (macFinal.isNotBlank()) {
            "${macFinal.uppercase()}@${networkScope}"
        } else {
            "ip:${networkScope}:$ip"
        }
        // Only migrate the previous *network-scoped* key format. Unscoped MAC/IP
        // and BSSID fallbacks can belong to another network and must never be reused.
        val legacyIds = if (legacyNetworkScope != networkScope) {
            listOf(
                if (macFinal.isNotBlank()) "${macFinal.uppercase()}@${legacyNetworkScope}"
                else "ip:${legacyNetworkScope}:$ip"
            )
        } else {
            emptyList()
        }
        val saved = loadCustomization(stableId, legacyIds)
        val previous = existingByStableId ?: existing
        val evidence = when {
            ip == localIp -> DiscoveryEvidence.LOCAL_INTERFACE
            hasTcpResponse -> DiscoveryEvidence.TCP_RESPONSE
            hasNsdServiceObservation -> DiscoveryEvidence.NSD_SERVICE_OBSERVATION
            hasSsdpObservation -> DiscoveryEvidence.SSDP_RESPONSE_OBSERVATION
            hasHostReachabilityProbe -> DiscoveryEvidence.HOST_REACHABILITY_PROBE
            ip == gateway -> DiscoveryEvidence.GATEWAY_CONFIGURATION
            macFinal.isNotBlank() -> DiscoveryEvidence.ARP_OBSERVATION
            else -> DiscoveryEvidence.UNKNOWN
        }
        val lastConfirmedOnline = when {
            ip == localIp || hasTcpResponse || hasNsdServiceObservation || hasSsdpObservation || hasHostReachabilityProbe -> now
            previousHasConfirmation(existingByStableId, existing) -> maxOf(
                existingByStableId?.lastConfirmedOnline ?: 0L,
                existing?.lastConfirmedOnline ?: 0L
            )
            else -> 0L
        }
        val firstSeen = when {
            saved.firstSeen > 0 -> saved.firstSeen
            previous != null && previous.firstSeen > 0 -> previous.firstSeen
            else -> now
        }

        // Do not use reverse DNS in the scan hot path; bounded NetBIOS + cached fallback only.
        val hostname = resolveHostname(ip).ifBlank { previous?.hostname.orEmpty() }
        val vendor = if (randomMac) "Random MAC" else lookupVendor(macFinal)
        val ports = previous?.openPorts.orEmpty()
        val httpFingerprint = previous?.httpFingerprint.orEmpty()
        val classification = DeviceTypeClassifier.classify(
            hostname = hostname,
            vendor = vendor.ifBlank { previous?.vendor.orEmpty() },
            ports = ports,
            isGateway = ip == gateway,
            httpFingerprint = httpFingerprint
        )
        return Device(
            ip = ip,
            mac = macFinal,
            hostname = hostname,
            vendor = vendor.ifBlank { previous?.vendor.orEmpty() },
            isLocalDevice = ip == localIp,
            isGateway = ip == gateway,
            firstSeen = firstSeen,
            lastSeen = now,
            lastConfirmedOnline = lastConfirmedOnline,
            discoveryEvidence = evidence,
            openPorts = ports,
            portsScannedAt = previous?.portsScannedAt ?: 0L,
            osGuess = previous?.osGuess ?: Device.guessOs(ports, vendor, hostname),
            nickname = saved.nickname.ifBlank { previous?.nickname.orEmpty() },
            notes = saved.notes.ifBlank { previous?.notes.orEmpty() },
            isFavorite = saved.isFavorite || (previous?.isFavorite == true),
            isWatched = saved.isWatched || (previous?.isWatched == true),
            networkBssid = bssid,
            networkScope = networkScope,
            deviceType = classification.type,
            typeConfidence = classification.confidence,
            typeEvidence = classification.signals,
            httpFingerprint = httpFingerprint,
            protocolEvidence = (previous?.protocolEvidence.orEmpty() + protocolEvidence)
                .distinct()
                .take(MAX_PROTOCOL_EVIDENCE),
            isRandomMac = randomMac,
            tags = previous?.tags.orEmpty(),
            auditedPorts = previous?.auditedPorts.orEmpty(),
            auditedPortResults = previous?.auditedPortResults.orEmpty()
        )
    }

    private companion object { const val MAX_PROTOCOL_EVIDENCE = 32 }

    private fun previousHasConfirmation(primary: Device?, fallback: Device?): Boolean =
        (primary?.lastConfirmedOnline ?: 0L) > 0L || (fallback?.lastConfirmedOnline ?: 0L) > 0L
}
