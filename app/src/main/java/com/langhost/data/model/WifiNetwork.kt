package com.langhost.data.model

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalDbm: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val security: String,
    val isCurrent: Boolean = false
) {
    val frequencyBand: String
        get() = when {
            frequencyMhz in 2400..2500 -> "2.4 GHz"
            frequencyMhz in 4900..5900 -> "5 GHz"
            frequencyMhz in 5900..7200 -> "6 GHz"
            else -> "—"
        }

    val signalLevel: Int
        get() = when {
            signalDbm >= -50 -> 4
            signalDbm >= -60 -> 3
            signalDbm >= -70 -> 2
            signalDbm >= -80 -> 1
            else -> 0
        }
}
