package com.langhost.data.model

data class NetworkInfo(
    val ssid: String = "—",
    val bssid: String = "—",
    val signalDbm: Int = 0,
    val frequencyMhz: Int = 0,
    val linkSpeedMbps: Int = 0,
    val channel: Int = 0,
    val security: String = "—",
    val ipAddress: String = "—",
    val gateway: String = "—",
    val netmask: String = "—",
    val dns1: String = "—",
    val dns2: String = "—",
    val networkId: Int = -1,
    val isConnected: Boolean = false
) {
    val signalLevel: Int
        get() = when {
            signalDbm == 0 -> 0 // 0 means unavailable in this model, not an exceptionally strong signal.
            signalDbm >= -50 -> 4
            signalDbm >= -60 -> 3
            signalDbm >= -70 -> 2
            signalDbm >= -80 -> 1
            else -> 0
        }

    val frequencyBand: String
        get() = when {
            frequencyMhz in 2400..2500 -> "2.4 GHz"
            frequencyMhz in 4900..5900 -> "5 GHz"
            frequencyMhz in 5900..7200 -> "6 GHz"
            else -> "—"
        }
}
