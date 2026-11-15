package com.langhost.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.langhost.ui.screens.channels.ChannelAnalyzerScreen
import com.langhost.ui.screens.dashboard.DashboardScreen
import com.langhost.ui.screens.device.DeviceDetailScreen
import com.langhost.ui.screens.devices.DevicesScreen
import com.langhost.ui.screens.internet.PublicIpScreen
import com.langhost.ui.screens.security.SecurityScreen
import com.langhost.ui.screens.settings.SettingsScreen
import com.langhost.ui.screens.tools.UtilitiesScreen
import com.langhost.ui.screens.topology.TopologyScreen
import com.langhost.ui.screens.wifi.NearbyNetworksScreen
import com.langhost.ui.screens.wol.WolProfilesScreen
import com.langhost.ui.theme.Divider

object Routes {
    const val DASHBOARD = "dashboard"
    const val DEVICES = "devices"
    const val DEVICE_DETAIL = "device/{deviceIp}"
    const val SETTINGS = "settings"
    const val NEARBY_WIFI = "nearby_wifi"
    const val SECURITY = "security"
    const val UTILITIES = "utilities"
    const val TOPOLOGY = "topology"
    const val WOL = "wol"
    const val CHANNELS = "channels"
    const val PUBLIC_IP = "public_ip"

    fun deviceDetail(ip: String) = "device/${ip.replace('.', '_')}"
    fun decodeIp(encoded: String) = encoded.replace('_', '.')
}

@Composable
fun LanGhostNavHost(windowSizeClass: WindowSizeClass? = null) {
    val isExpanded = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
    if (isExpanded) TabletNavHost() else PhoneNavHost()
}

@Composable
private fun PhoneNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToDevices = { navController.navigate(Routes.DEVICES) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToNearbyWifi = { navController.navigate(Routes.NEARBY_WIFI) },
                onNavigateToSecurity = { navController.navigate(Routes.SECURITY) },
                onNavigateToUtilities = { navController.navigate(Routes.UTILITIES) },
                onNavigateToTopology = { navController.navigate(Routes.TOPOLOGY) },
                onNavigateToWol = { navController.navigate(Routes.WOL) },
                onNavigateToChannels = { navController.navigate(Routes.CHANNELS) },
                onNavigateToPublicIp = { navController.navigate(Routes.PUBLIC_IP) }
            )
        }
        composable(Routes.DEVICES) {
            DevicesScreen(
                onBack = { navController.popBackStack() },
                onDeviceClick = { ip -> navController.navigate(Routes.deviceDetail(ip)) }
            )
        }
        composable(Routes.DEVICE_DETAIL, arguments = listOf(navArgument("deviceIp") { type = NavType.StringType })) { entry ->
            DeviceDetailScreen(Routes.decodeIp(entry.arguments?.getString("deviceIp") ?: ""), onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NEARBY_WIFI) { NearbyNetworksScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SECURITY) { SecurityScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.UTILITIES) { UtilitiesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.TOPOLOGY) {
            TopologyScreen(onBack = { navController.popBackStack() }, onDeviceClick = { navController.navigate(Routes.deviceDetail(it)) })
        }
        composable(Routes.WOL) { WolProfilesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CHANNELS) { ChannelAnalyzerScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PUBLIC_IP) { PublicIpScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun TabletNavHost() {
    val navController = rememberNavController()
    var selectedDeviceIp by rememberSaveable { mutableStateOf<String?>(null) }
    Row(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.DEVICES, modifier = Modifier.weight(0.4f)) {
            composable(Routes.DEVICES) {
                DevicesScreen(onBack = { selectedDeviceIp = null }, onDeviceClick = { selectedDeviceIp = it })
            }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.NEARBY_WIFI) { NearbyNetworksScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SECURITY) { SecurityScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.UTILITIES) { UtilitiesScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.TOPOLOGY) {
                TopologyScreen(onBack = { navController.popBackStack() }, onDeviceClick = { selectedDeviceIp = it })
            }
            composable(Routes.WOL) { WolProfilesScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.CHANNELS) { ChannelAnalyzerScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.PUBLIC_IP) { PublicIpScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onNavigateToDevices = { navController.navigate(Routes.DEVICES) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToNearbyWifi = { navController.navigate(Routes.NEARBY_WIFI) },
                    onNavigateToSecurity = { navController.navigate(Routes.SECURITY) },
                    onNavigateToUtilities = { navController.navigate(Routes.UTILITIES) },
                    onNavigateToTopology = { navController.navigate(Routes.TOPOLOGY) },
                    onNavigateToWol = { navController.navigate(Routes.WOL) },
                    onNavigateToChannels = { navController.navigate(Routes.CHANNELS) },
                    onNavigateToPublicIp = { navController.navigate(Routes.PUBLIC_IP) }
                )
            }
        }
        VerticalDivider(color = Divider, thickness = 1.dp)
        if (selectedDeviceIp != null) {
            DeviceDetailScreen(selectedDeviceIp!!, onBack = { selectedDeviceIp = null }, modifier = Modifier.weight(0.6f))
        } else {
            DashboardScreen(
                onNavigateToDevices = { navController.navigate(Routes.DEVICES) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToNearbyWifi = { navController.navigate(Routes.NEARBY_WIFI) },
                onNavigateToSecurity = { navController.navigate(Routes.SECURITY) },
                onNavigateToUtilities = { navController.navigate(Routes.UTILITIES) },
                onNavigateToTopology = { navController.navigate(Routes.TOPOLOGY) },
                onNavigateToWol = { navController.navigate(Routes.WOL) },
                onNavigateToChannels = { navController.navigate(Routes.CHANNELS) },
                onNavigateToPublicIp = { navController.navigate(Routes.PUBLIC_IP) },
                modifier = Modifier.weight(0.6f)
            )
        }
    }
}
