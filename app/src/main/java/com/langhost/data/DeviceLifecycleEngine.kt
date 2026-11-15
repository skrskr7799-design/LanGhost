package com.langhost.data

import com.langhost.data.model.Device

/**
 * Pure reconciliation of a completed discovery snapshot with devices retained
 * from earlier scans on the same logical network.
 *
 * The repository owns cache access and atomic publication. This class owns only
 * deterministic lifecycle policy: online snapshots replace stale snapshots,
 * recently seen devices remain visible as offline, and identities never cross
 * network scopes.
 */
class DeviceLifecycleEngine(
    private val offlineRetentionMs: Long = DEFAULT_OFFLINE_RETENTION_MS,
    private val onlineWindowMs: Long = DEFAULT_ONLINE_WINDOW_MS
) {
    init {
        require(offlineRetentionMs >= 0L)
        require(onlineWindowMs > 0L)
    }

    fun reconcile(
        discoveredOnline: List<Device>,
        cachedDevices: Collection<Device>,
        networkScope: String,
        now: Long
    ): List<Device> {
        if (networkScope.isBlank()) return emptyList()

        val onlineById = discoveredOnline
            .asSequence()
            .filter { it.networkScope == networkScope }
            .groupBy { it.stableId }
            .mapValues { (_, snapshots) -> selectSnapshot(snapshots) }

        val offlineById = cachedDevices
            .asSequence()
            .filter { device ->
                device.networkScope == networkScope &&
                    device.stableId !in onlineById &&
                    now - device.lastSeen >= 0L &&
                    now - device.lastSeen < offlineRetentionMs
            }
            .groupBy { it.stableId }
            .mapValues { (_, snapshots) -> selectSnapshot(snapshots) }

        return (onlineById.values + offlineById.values)
            .sortedWith(
                compareByDescending<Device> { isOnlineAt(it, now) }
                    .thenByDescending { it.isGateway }
                    .thenByDescending { it.isLocalDevice }
                    .thenByDescending { it.isFavorite }
                    .thenBy { it.ip }
                    .thenBy { it.stableId }
            )
    }

    private fun selectSnapshot(snapshots: List<Device>): Device =
        snapshots.sortedWith(
            compareByDescending<Device> { it.lastSeen }
                .thenBy { it.ip }
                .thenBy { it.mac.uppercase() }
        ).first()

    private fun isOnlineAt(device: Device, now: Long): Boolean =
        now - device.lastSeen < onlineWindowMs

    companion object {
        const val DEFAULT_OFFLINE_RETENTION_MS = 24L * 60L * 60L * 1000L
        const val DEFAULT_ONLINE_WINDOW_MS = 10L * 60L * 1000L
    }
}
