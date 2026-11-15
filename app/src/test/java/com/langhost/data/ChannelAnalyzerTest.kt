package com.langhost.data

import com.langhost.data.model.WifiNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelAnalyzerTest {

    @Test
    fun returnsAllPrimaryChannelsAndARecommendation() {
        val advice = ChannelAnalyzer.analyze(emptyList(), currentChannel = 6)

        assertEquals(3, advice.band24.size)
        assertEquals(listOf(1, 6, 11), advice.band24.map { it.channel })
        assertNotNull(advice.best24)
        assertNotNull(advice.best5)
        assertTrue(advice.recommendation.contains("Сейчас канал 6"))
    }

    @Test
    fun nearbyNetworksContributeToChannelLoad() {
        val networks = listOf(
            WifiNetwork("A", "00:00:00:00:00:01", -45, 2437, 6, "WPA2"),
            WifiNetwork("B", "00:00:00:00:00:02", -70, 2412, 1, "WPA2")
        )

        val advice = ChannelAnalyzer.analyze(networks, currentChannel = 0)
        val channelOne = advice.band24.first { it.channel == 1 }
        val channelSix = advice.band24.first { it.channel == 6 }

        assertTrue(channelOne.networkCount > 0)
        assertTrue(channelSix.networkCount > 0)
        assertTrue(channelSix.score > 0)
    }
}
