package com.langhost.data

import com.langhost.data.model.Device

data class ScanDiffResult(
    val appeared: List<Device>,
    val disappeared: List<Device>,
    val portsChanged: List<Pair<Device, Pair<List<Int>, List<Int>>>>
)

object ScanDiff {
    private const val MAX_TRACKED_NETWORKS = 8
    private val previousByNetwork = LinkedHashMap<String, Map<String, Device>>()

    @Synchronized
    fun updateAndDiff(current: List<Device>, networkScope: String = "default"): ScanDiffResult {
        val previous = previousByNetwork[networkScope].orEmpty()
        val currMap = current.associateBy { it.stableId }
        val appeared = current.filter { it.stableId !in previous && it.isOnline }
        val disappeared = previous.values.filter { it.stableId !in currMap }
        val portsChanged = current.mapNotNull { device ->
            val old = previous[device.stableId] ?: return@mapNotNull null
            if (old.openPorts.toSet() != device.openPorts.toSet() && device.openPorts.isNotEmpty()) {
                device to (old.openPorts to device.openPorts)
            } else null
        }

        previousByNetwork.remove(networkScope)
        previousByNetwork[networkScope] = currMap
        while (previousByNetwork.size > MAX_TRACKED_NETWORKS) {
            previousByNetwork.remove(previousByNetwork.keys.first())
        }
        return ScanDiffResult(appeared, disappeared, portsChanged)
    }

    @Synchronized
    fun clear() {
        previousByNetwork.clear()
    }
}
