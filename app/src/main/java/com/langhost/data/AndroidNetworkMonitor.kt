package com.langhost.data

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.langhost.data.model.NetworkInfo
import com.langhost.data.network.WifiChannel
import java.util.concurrent.ConcurrentHashMap

internal data class AndroidNetworkSnapshot(
    val info: NetworkInfo,
    val activeNetwork: Network?
)

/**
 * Owns Android connectivity callbacks and platform-specific network inspection.
 * It does not publish application state or decide whether a snapshot is still
 * current; NetworkRepository performs that synchronized commit.
 */
internal class AndroidNetworkMonitor(
    private val connectivityManager: ConnectivityManager,
    private val wifiManager: WifiManager,
    private val onDefaultNetworkChanged: () -> Unit
) {
    private val wifiInfoByNetwork = ConcurrentHashMap<Network, WifiInfo>()

    private val callback: ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
            ) {
                override fun onAvailable(network: Network) {
                    if (connectivityManager.activeNetwork == network) onDefaultNetworkChanged()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    cacheWifiInfo(network, capabilities)
                    if (connectivityManager.activeNetwork == network) onDefaultNetworkChanged()
                }

                override fun onLost(network: Network) {
                    wifiInfoByNetwork.remove(network)
                    onDefaultNetworkChanged()
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (connectivityManager.activeNetwork == network) onDefaultNetworkChanged()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    cacheWifiInfo(network, capabilities)
                    if (connectivityManager.activeNetwork == network) onDefaultNetworkChanged()
                }

                override fun onLost(network: Network) {
                    wifiInfoByNetwork.remove(network)
                    onDefaultNetworkChanged()
                }
            }
        }

    fun start() {
        try {
            // Observe the default route across Wi-Fi, Ethernet, VPN and other transports.
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            AppLog.warning(AppLog.Component.NETWORK_MONITOR, AppLog.Event.CALLBACK_REGISTRATION_FAILED)
        }
    }

    private fun cacheWifiInfo(network: Network, capabilities: NetworkCapabilities) {
        // NetworkCapabilities.transportInfo was added in API 29. Android 8–9 use
        // WifiManager.connectionInfo in readSnapshot() instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            (capabilities.transportInfo as? WifiInfo)?.let { wifiInfoByNetwork[network] = it }
        } else {
            wifiInfoByNetwork.remove(network)
        }
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    fun readSnapshot(): AndroidNetworkSnapshot {
        val network = connectivityManager.activeNetwork
        if (network == null) return AndroidNetworkSnapshot(NetworkInfo(), null)

        return try {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val linkProperties = connectivityManager.getLinkProperties(network)
            if (capabilities == null || linkProperties == null) {
                return AndroidNetworkSnapshot(NetworkInfo(), network)
            }

            val localAddress = linkProperties.linkAddresses
                .asSequence()
                .mapNotNull { it.address as? java.net.Inet4Address }
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }

            val gateway = linkProperties.routes
                .asSequence()
                .filter { it.isDefaultRoute }
                .mapNotNull { it.gateway as? java.net.Inet4Address }
                .firstOrNull()

            val dnsServers = linkProperties.dnsServers
                .filterIsInstance<java.net.Inet4Address>()
                .mapNotNull { it.hostAddress }

            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val wifiInfo = if (isWifi) {
                wifiInfoByNetwork[network] ?: if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager.connectionInfo
                } else {
                    // Android 12+ redacts location-sensitive fields unless the callback
                    // opted into location info and the app has the required permissions.
                    capabilities.transportInfo as? WifiInfo
                }
            } else {
                null
            }

            val ssid = wifiInfo?.ssid
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID && it != "<unknown ssid>" }
                ?: "—"
            val bssid = wifiInfo?.bssid
                ?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }
                ?: "—"
            val frequency = wifiInfo?.frequency?.takeIf { it > 0 } ?: 0
            val prefixLength = linkProperties.linkAddresses
                .firstOrNull { it.address is java.net.Inet4Address && it.address == localAddress }
                ?.prefixLength
            val netmask = prefixLength?.let(::prefixToNetmask) ?: "0.0.0.0"
            val ip = localAddress?.hostAddress ?: "0.0.0.0"

            AndroidNetworkSnapshot(
                info = NetworkInfo(
                    ssid = ssid,
                    bssid = bssid,
                    signalDbm = wifiInfo?.rssi?.takeIf { it in -127..0 } ?: 0,
                    frequencyMhz = frequency,
                    linkSpeedMbps = wifiInfo?.linkSpeed?.takeIf { it > 0 } ?: 0,
                    channel = WifiChannel.fromFrequencyMhz(frequency),
                    security = wifiSecurityType(wifiInfo),
                    ipAddress = ip,
                    gateway = gateway?.hostAddress ?: "0.0.0.0",
                    netmask = netmask,
                    dns1 = dnsServers.getOrNull(0) ?: "0.0.0.0",
                    dns2 = dnsServers.getOrNull(1) ?: "0.0.0.0",
                    networkId = wifiInfo?.networkId ?: -1,
                    interfaceName = linkProperties.interfaceName.orEmpty(),
                    isConnected = localAddress != null && (isWifi ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                ),
                activeNetwork = network
            )
        } catch (_: Exception) {
            AppLog.warning(AppLog.Component.NETWORK_MONITOR, AppLog.Event.SNAPSHOT_READ_FAILED)
            AndroidNetworkSnapshot(NetworkInfo(), connectivityManager.activeNetwork)
        }
    }

    private fun wifiSecurityType(wifiInfo: WifiInfo?): String {
        if (wifiInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "—"
        return when (wifiInfo.currentSecurityType) {
            WifiInfo.SECURITY_TYPE_OPEN -> "Open"
            WifiInfo.SECURITY_TYPE_WEP -> "WEP"
            WifiInfo.SECURITY_TYPE_PSK -> "WPA-PSK"
            WifiInfo.SECURITY_TYPE_EAP -> "WPA-Enterprise"
            WifiInfo.SECURITY_TYPE_SAE -> "WPA3-SAE"
            WifiInfo.SECURITY_TYPE_OWE -> "OWE"
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3-Enterprise"
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3-Enterprise-192"
            WifiInfo.SECURITY_TYPE_WAPI_PSK -> "WAPI-PSK"
            WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI-CERT"
            WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2 -> "Passpoint"
            WifiInfo.SECURITY_TYPE_PASSPOINT_R3 -> "Passpoint R3"
            else -> "Unknown"
        }
    }

    private fun prefixToNetmask(prefixLength: Int): String {
        if (prefixLength !in 0..32) return "0.0.0.0"
        val mask = if (prefixLength == 0) 0L else
            (0xFFFF_FFFFL shl (32 - prefixLength)) and 0xFFFF_FFFFL
        return listOf(
            (mask shr 24) and 0xFF,
            (mask shr 16) and 0xFF,
            (mask shr 8) and 0xFF,
            mask and 0xFF
        ).joinToString(".")
    }
}
