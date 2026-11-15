package com.langhost.data

import com.langhost.data.model.WifiNetwork

data class ChannelLoad(
    val channel: Int,
    val band: String, // "2.4" or "5"
    val networkCount: Int,
    val strongestDbm: Int,
    val score: Int // lower = better for us (less congestion)
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

        fun loads(list: List<WifiNetwork>, band: String, candidates: IntRange): List<ChannelLoad> {
            return candidates.map { ch ->
                val onCh = list.filter { it.channel == ch }
                // Overlap penalty for 2.4 GHz (adjacent channels)
                val overlap = if (band == "2.4") {
                    list.count { kotlin.math.abs(it.channel - ch) <= 2 && it.channel != ch }
                } else 0
                val count = onCh.size
                val strongest = onCh.maxOfOrNull { it.signalDbm } ?: -100
                val score = count * 10 + overlap * 3 + if (strongest > -50) 5 else 0
                ChannelLoad(ch, band, count, strongest, score)
            }.sortedBy { it.score }
        }

        val band24 = loads(g24, "2.4", 1..13)
        val band5 = loads(g5, "5", listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 149, 153, 157, 161, 165).let { chs ->
            // Only show channels that appear or common ones with activity nearby
            val present = g5.map { it.channel }.toSet()
            if (present.isEmpty()) chs.take(8)
            else (present + chs.take(6)).distinct().sorted()
        }.let { sorted ->
            if (sorted.isEmpty()) 36..36 else sorted.first()..sorted.last() // placeholder replaced below
        })

        // Simpler 5 GHz: group by actual channels present + recommend least used common
        val common5 = listOf(36, 40, 44, 48, 149, 153, 157, 161)
        val band5Real = common5.map { ch ->
            val onCh = g5.filter { it.channel == ch }
            ChannelLoad(ch, "5", onCh.size, onCh.maxOfOrNull { it.signalDbm } ?: -100, onCh.size * 10)
        }.sortedBy { it.score }

        val best24 = band24.filter { it.channel in listOf(1, 6, 11) }.minByOrNull { it.score }?.channel
            ?: band24.firstOrNull()?.channel
        val best5 = band5Real.firstOrNull()?.channel

        val rec = buildString {
            if (best24 != null) append("2.4 ГГц: лучше канал $best24")
            if (best5 != null) {
                if (isNotEmpty()) append(". ")
                append("5 ГГц: лучше канал $best5")
            }
            if (currentChannel > 0) {
                append(". Сейчас у вас канал $currentChannel")
                val curLoad = (band24 + band5Real).find { it.channel == currentChannel }
                if (curLoad != null && curLoad.networkCount > 2) {
                    append(" — загружен (${curLoad.networkCount} сетей)")
                }
            }
            if (isEmpty()) append("Недостаточно данных — просканируйте соседние сети")
        }

        return ChannelAdvice(
            band24 = band24.filter { it.networkCount > 0 || it.channel in listOf(1, 6, 11) },
            band5 = band5Real,
            best24 = best24,
            best5 = best5,
            recommendation = rec
        )
    }
}
