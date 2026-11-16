package com.langhost.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiChannelTest {
    @Test
    fun mapsStandardChannelsAcrossWifiBands() {
        assertEquals(1, WifiChannel.fromFrequencyMhz(2412))
        assertEquals(6, WifiChannel.fromFrequencyMhz(2437))
        assertEquals(13, WifiChannel.fromFrequencyMhz(2472))
        assertEquals(14, WifiChannel.fromFrequencyMhz(2484))
        assertEquals(36, WifiChannel.fromFrequencyMhz(5180))
        assertEquals(149, WifiChannel.fromFrequencyMhz(5745))
        assertEquals(2, WifiChannel.fromFrequencyMhz(5935))
        assertEquals(1, WifiChannel.fromFrequencyMhz(5955))
        assertEquals(37, WifiChannel.fromFrequencyMhz(6135))
        assertEquals(233, WifiChannel.fromFrequencyMhz(7115))
    }

    @Test
    fun returnsZeroForUnknownOrNonChannelFrequencies() {
        assertEquals(0, WifiChannel.fromFrequencyMhz(0))
        assertEquals(0, WifiChannel.fromFrequencyMhz(2400))
        assertEquals(0, WifiChannel.fromFrequencyMhz(5202))
        assertEquals(0, WifiChannel.fromFrequencyMhz(8000))
    }
}
