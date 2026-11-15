package com.langhost.data

import com.langhost.data.model.Device

data class ScanDiffResult(
    val appeared: List<Device>,
    val disappeared: List<Device>,
    val portsChanged: List<Pair<Device, Pair<List<Int>, List<Int>>>>
)

object ScanDiff {
    private var previous: Map<String, Device> = emptyMap()

    fun updateAndDiff(current: List<Device>): ScanDiffResult {
        val currMap = current.associateBy { it.stableId }
        val appeared = current.filter { it.stableId !in previous && it.isOnline }
        val disappeared = previous.values.filter { it.stableId !in currMap }
        val portsChanged = current.mapNotNull { d ->
            val old = previous[d.stableId] ?: return@mapNotNull null
            if (old.openPorts.toSet() != d.openPorts.toSet() && d.openPorts.isNotEmpty()) {
                d to (old.openPorts to d.openPorts)
            } else null
        }
        previous = currMap
        return ScanDiffResult(appeared, disappeared, portsChanged)
    }

    fun clear() {
        previous = emptyMap()
    }
}
