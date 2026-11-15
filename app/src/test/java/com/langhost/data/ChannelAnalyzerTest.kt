package com.langhost.data

import com.langhost.data.model.WifiNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelAnalyzerTest {

    @Test
    fun emptyScanDoesNotInventAChannelRecommendation() {
        val advice = ChannelAnalyzer.analyze(emptyList(), currentChannel = 6)

        assertEquals(listOf(1, 6, 11), advice.band24.map { it.channel })
        assertNull(advice.best24)
        assertNull(advice.best5)
        assertNull(advice.best6)
        assertTrue(advice.recommendation.contains("Недостаточно данных"))
        assertTrue(advice.recommendation.contains("Сейчас используется канал 6"))
    }

    @Test
    fun nearbyNetworksContributeToChannelLoad() {
        val networks = listOf(
            WifiNetwork("A", "00:00:00:00:00:01", -45, 2437, 6, "WPA2"),
            WifiNetwork("B", "00:00:00:00:00:02", -70, 2412, 1, "WPA2"),
            WifiNetwork("C", "00:00:00:00:00:03", -55, 2422, 3, "WPA2")
        )

        val advice = ChannelAnalyzer.analyze(networks, currentChannel = 0)
        val channelOne = advice.band24.first { it.channel == 1 }
        val channelSix = advice.band24.first { it.channel == 6 }

        assertTrue(channelOne.networkCount >= 2)
        assertTrue(channelSix.networkCount >= 2)
        assertTrue(channelOne.score > 0)
        assertTrue(channelSix.score > 0)
        assertTrue(advice.best24 in listOf(1, 6, 11))
    }

    @Test
    fun includesObservedSixGhzNetworksAndRecommendations() {
        val networks = listOf(
            WifiNetwork("Six", "00:00:00:00:00:06", -48, 5955, 1, "WPA3")
        )

        val advice = ChannelAnalyzer.analyze(networks, currentChannel = 0)

        assertTrue(advice.band6.isNotEmpty())
        assertEquals(1, advice.band6.first { it.channel == 1 }.networkCount)
        assertTrue(advice.best6 != null)
        assertTrue(advice.recommendation.contains("6 ГГц"))
    }
}
