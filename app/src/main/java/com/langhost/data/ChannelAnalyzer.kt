package com.langhost.data

import com.langhost.data.model.WifiNetwork
import kotlin.math.abs
import kotlin.math.roundToInt

data class ChannelLoad(
    val channel: Int,
    val band: String,
    /** Networks whose occupied spectrum overlaps this channel. */
    val networkCount: Int,
    val strongestDbm: Int,
    /** Relative interference estimate; lower is better. */
    val score: Int
)

data class ChannelAdvice(
    val band24: List<ChannelLoad>,
    val band5: List<ChannelLoad>,
    val band6: List<ChannelLoad>,
    val best24: Int?,
    val best5: Int?,
    val best6: Int?,
    val recommendation: String
)

/**
 * Estimates channel congestion from the frequencies and signal levels actually observed.
 *
 * This is a planning aid, not a regulatory-channel validator: available channels depend
 * on the router's country code, hardware and channel width. The analyzer never assumes
 * that a channel is supported merely because it appears in the candidate list.
 */
object ChannelAnalyzer {
    private val preferred24 = listOf(1, 6, 11)
    private val channels5 = buildList {
        addAll((36..64 step 4).toList())
        addAll((100..144 step 4).toList())
        addAll((149..177 step 4).toList())
    }
    private val channels6 = (1..233 step 4).toList()

    fun analyze(networks: List<WifiNetwork>, currentChannel: Int): ChannelAdvice {
        val g24 = networks.filter { it.frequencyMhz in 2400..2500 && it.channel in 1..13 }
        val g5 = networks.filter { it.frequencyMhz in 4900..5900 && it.channel > 0 }
        val g6 = networks.filter { it.frequencyMhz in 5900..7200 && it.channel > 0 }

        val band24 = preferred24.map { channel ->
            val center = frequency24(channel)
            val overlapping = g24.filter { abs(it.frequencyMhz - center) < 25 }
            load(channel, "2.4", overlapping, center, 25)
        }.sortedBy { it.score }

        val band5 = channels5.map { channel ->
            val center = 5000 + channel * 5
            val observed = g5.filter { it.channel == channel }
            load(channel, "5", observed, center, 0)
        }.sortedBy { it.score }

        val band6 = channels6.map { channel ->
            val center = 5950 + channel * 5
            val observed = g6.filter { it.channel == channel }
            load(channel, "6", observed, center, 0)
        }.sortedBy { it.score }

        val best24 = if (g24.isNotEmpty()) band24.minByOrNull { it.score }?.channel else null
        val best5 = if (g5.isNotEmpty()) band5.minByOrNull { it.score }?.channel else null
        val best6 = if (g6.isNotEmpty()) band6.minByOrNull { it.score }?.channel else null

        val recommendation = buildList {
            if (best24 != null) add("2.4 ГГц: наименьшая оценка помех у канала $best24")
            if (best5 != null && g5.isNotEmpty()) add("5 ГГц: среди проверенных каналов свободнее $best5")
            if (best6 != null) add("6 ГГц: среди проверенных каналов свободнее $best6")
            if (currentChannel > 0) add("Сейчас используется канал $currentChannel")
            if (isEmpty()) add("Недостаточно данных для рекомендации. Просканируйте соседние сети")
        }.joinToString(". ")

        return ChannelAdvice(
            band24 = band24,
            band5 = band5,
            band6 = band6,
            best24 = best24,
            best5 = best5,
            best6 = best6,
            recommendation = recommendation
        )
    }

    private fun load(
        channel: Int,
        band: String,
        overlapping: List<WifiNetwork>,
        centerFrequencyMhz: Int,
        overlapWidthMhz: Int
    ): ChannelLoad {
        val score = overlapping.sumOf { network ->
            val distance = abs(network.frequencyMhz - centerFrequencyMhz)
            val proximity = if (overlapWidthMhz == 0) {
                if (distance == 0) 1.0 else 0.0
            } else {
                (1.0 - distance.toDouble() / overlapWidthMhz).coerceAtLeast(0.0)
            }
            // Stronger nearby access points contribute more to the estimate.
            val signalWeight = ((network.signalDbm + 100) / 60.0).coerceIn(0.0, 1.0)
            (proximity * (1.0 + signalWeight * 2.0) * 100).roundToInt()
        }
        return ChannelLoad(
            channel = channel,
            band = band,
            networkCount = overlapping.size,
            strongestDbm = overlapping.maxOfOrNull { it.signalDbm } ?: -100,
            score = score
        )
    }

    private fun frequency24(channel: Int): Int = 2412 + (channel - 1) * 5
}
