package com.langhost.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkInfoTest {

    @Test
    fun signalLevelUsesExpectedThresholds() {
        assertEquals(4, NetworkInfo(signalDbm = -50).signalLevel)
        assertEquals(3, NetworkInfo(signalDbm = -60).signalLevel)
        assertEquals(2, NetworkInfo(signalDbm = -70).signalLevel)
        assertEquals(1, NetworkInfo(signalDbm = -80).signalLevel)
        assertEquals(0, NetworkInfo(signalDbm = -81).signalLevel)
        assertEquals(0, NetworkInfo(signalDbm = 0).signalLevel)
    }

    @Test
    fun frequencyBandRecognizesWifiBands() {
        assertEquals("2.4 GHz", NetworkInfo(frequencyMhz = 2412).frequencyBand)
        assertEquals("5 GHz", NetworkInfo(frequencyMhz = 5180).frequencyBand)
        assertEquals("6 GHz", NetworkInfo(frequencyMhz = 6105).frequencyBand)
        assertEquals("—", NetworkInfo(frequencyMhz = 900).frequencyBand)
    }
}
