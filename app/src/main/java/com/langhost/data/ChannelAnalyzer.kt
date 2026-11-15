package com.langhost.data

import com.langhost.data.model.WifiNetwork

data class ChannelLoad(
    val channel: Int,
    val band: String,
    val networkCount: Int,
    val strongestDbm: Int,
    val score: Int
)

data class ChannelAdvice(
    val band24: List<ChannelLoad>,
    val band5: List<ChannelLoad>,
    val best24: Int?,
    val best5: Int?,
    val recommendation: String
)

object ChannelAnalyzer {

    fun analyze(networks: List<WifiNetwork>, currentChannel: Int): ChannelAdvice {
        val g24 = networks.filter { it.frequencyMhz in 2400..2500 }
        val g5 = networks.filter { it.frequencyMhz in 4900..5900 }

        val band24 = (1..13).map { ch ->
            val onCh = g24.filter { it.channel == ch }
            val overlap = g24.count { kotlin.math.abs(it.channel - ch) <= 2 && it.channel != ch }
            val count = onCh.size
            val strongest = onCh.maxOfOrNull { it.signalDbm } ?: -100
            val score = count * 10 + overlap * 3 + if (strongest > -50) 5 else 0
            ChannelLoad(ch, "2.4", count, strongest, score)
        }.sortedBy { it.score }

        val common5 = listOf(36, 40, 44, 48, 149, 153, 157, 161)
        val band5 = common5.map { ch ->
            val onCh = g5.filter { it.channel == ch }
            ChannelLoad(ch, "5", onCh.size, onCh.maxOfOrNull { it.signalDbm } ?: -100, onCh.size * 10)
        }.sortedBy { it.score }

        val best24 = band24.filter { it.channel in listOf(1, 6, 11) }.minByOrNull { it.score }?.channel
            ?: band24.firstOrNull()?.channel
        val best5 = band5.firstOrNull()?.channel

        val rec = buildString {
            if (best24 != null) append("2.4 ГГц: лучше канал $best24")
            if (best5 != null) {
                if (isNotEmpty()) append(". ")
                append("5 ГГц: лучше канал $best5")
            }
            if (currentChannel > 0) append(". Сейчас канал $currentChannel")
            if (isEmpty()) append("Просканируйте соседние сети")
        }

        return ChannelAdvice(
            band24 = band24.filter { it.networkCount > 0 || it.channel in listOf(1, 6, 11) },
            band5 = band5,
            best24 = best24,
            best5 = best5,
            recommendation = rec
        )
    }
}
