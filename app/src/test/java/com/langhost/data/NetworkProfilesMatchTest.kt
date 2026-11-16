package com.langhost.data

import com.langhost.data.model.NetworkInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkProfilesMatchTest {
    private val profile = NetworkProfile(
        id = "home",
        name = "Home",
        ssid = "Home Wi-Fi",
        bssid = "AA:BB:CC:DD:EE:FF"
    )

    @Test
    fun exactMatchRequiresSameSsidAndBssid() {
        val network = NetworkInfo(
            ssid = "Home Wi-Fi",
            bssid = "aa:bb:cc:dd:ee:ff",
            isConnected = true
        )

        assertEquals(ProfileConnectionMatch.Exact, matchNetworkProfile(profile, network))
    }

    @Test
    fun missingBssidProducesOnlySsidMatch() {
        val network = NetworkInfo(
            ssid = "Home Wi-Fi",
            bssid = "—",
            isConnected = true
        )

        assertEquals(ProfileConnectionMatch.SsidOnly, matchNetworkProfile(profile, network))
    }

    @Test
    fun differentAccessPointDoesNotMatchProfile() {
        val network = NetworkInfo(
            ssid = "Home Wi-Fi",
            bssid = "11:22:33:44:55:66",
            isConnected = true
        )

        assertEquals(ProfileConnectionMatch.None, matchNetworkProfile(profile, network))
    }

    @Test
    fun disconnectedNetworkNeverMatchesEvenWhenIdentifiersRemain() {
        val network = NetworkInfo(
            ssid = "Home Wi-Fi",
            bssid = "AA:BB:CC:DD:EE:FF",
            isConnected = false
        )

        assertEquals(ProfileConnectionMatch.None, matchNetworkProfile(profile, network))
    }

    @Test
    fun unknownSsidAndBssidSentinelsAreNotTreatedAsEvidence() {
        val network = NetworkInfo(
            ssid = "—",
            bssid = "—",
            isConnected = true
        )

        assertEquals(ProfileConnectionMatch.None, matchNetworkProfile(profile, network))
    }
}
