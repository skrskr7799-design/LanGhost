package com.langhost.ui.navigation

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.Alignment

import androidx.compose.material3.Surface

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.langhost.ui.screens.channels.ChannelAnalyzerScreen
import com.langhost.ui.screens.dashboard.DashboardScreen
import com.langhost.ui.screens.device.DeviceDetailScreen
import com.langhost.ui.screens.devices.DevicesScreen
import com.langhost.ui.screens.internet.PublicIpScreen
import com.langhost.ui.screens.profiles.NetworkProfilesScreen
import com.langhost.ui.screens.security.SecurityScreen
import com.langhost.ui.screens.settings.SettingsScreen
import com.langhost.ui.screens.tools.UtilitiesScreen
import com.langhost.ui.screens.topology.TopologyScreen
import com.langhost.ui.screens.wifi.NearbyNetworksScreen
import com.langhost.ui.screens.wol.WolProfilesScreen
import com.langhost.ui.components.NetworkOrbit
import com.langhost.ui.theme.AccentCyan
import com.langhost.ui.theme.Surface
import com.langhost.ui.theme.Background
import com.langhost.ui.theme.Divider
import com.langhost.ui.theme.TextPrimary
import com.langhost.ui.theme.TextSecondary

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
    const val PROFILES = "profiles"

    fun deviceDetail(ip: String) = "device/${ip.replace('.', '_')}"
    fun decodeIp(encoded: String) = encoded.replace('_', '.')
}

private data class PrimaryDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val primaryDestinations = listOf(
    PrimaryDestination(Routes.DASHBOARD, "Обзор", Icons.Default.Home),
    PrimaryDestination(Routes.DEVICES, "Устройства", Icons.Default.Devices),
    PrimaryDestination(Routes.TOPOLOGY, "Топология", Icons.Default.AccountTree),
    PrimaryDestination(Routes.NEARBY_WIFI, "Wi-Fi", Icons.Default.Wifi)
)

@Composable
fun LanGhostNavHost(windowSizeClass: WindowSizeClass? = null) {
    val navController = rememberNavController()
    val expanded = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedRoute = primaryDestinations.firstOrNull { it.route == currentRoute }?.route

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (expanded) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(112.dp)
                    .fillMaxHeight()
                    .background(Surface)
                    .padding(horizontal = 8.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NetworkOrbit(isConnected = true, modifier = Modifier.size(46.dp))
                Text(
                    text = "LanGhost",
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(12.dp))
                primaryDestinations.forEach { destination ->
                    NavigationEntry(
                        destination = destination,
                        selected = selectedRoute == destination.route,
                        onClick = { navigateTo(destination.route) },
                        compact = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            androidx.compose.material3.VerticalDivider(color = Divider, thickness = 1.dp)
            AppNavGraph(
                navController = navController,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                Column(modifier = Modifier.background(Surface)) {
                    androidx.compose.material3.HorizontalDivider(color = Divider.copy(alpha = 0.75f), thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        primaryDestinations.forEach { destination ->
                            NavigationEntry(
                                destination = destination,
                                selected = selectedRoute == destination.route,
                                onClick = { navigateTo(destination.route) },
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            AppNavGraph(
                navController = navController,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        }
    }
}

@Composable
private fun NavigationEntry(
    destination: PrimaryDestination,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val iconTint = if (selected) AccentCyan else TextSecondary
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) SurfaceHighlight.copy(alpha = 0.72f) else Color.Transparent,
        contentColor = TextPrimary,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.65f)) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = if (compact) 6.dp else 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 30.dp else 38.dp)
                    .background(
                        if (selected) Primary.copy(alpha = 0.12f) else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.label,
                    tint = iconTint,
                    modifier = Modifier.size(if (compact) 20.dp else 22.dp)
                )
            }
            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) TextPrimary else TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AppNavGraph(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier
    ) {
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
                onNavigateToPublicIp = { navController.navigate(Routes.PUBLIC_IP) },
                onNavigateToProfiles = { navController.navigate(Routes.PROFILES) }
            )
        }
        composable(Routes.DEVICES) {
            DevicesScreen(
                onBack = { navController.popBackStack(Routes.DASHBOARD, false) },
                onDeviceClick = { navController.navigate(Routes.deviceDetail(it)) }
            )
        }
        composable(Routes.DEVICE_DETAIL, arguments = listOf(navArgument("deviceIp") { type = NavType.StringType })) { entry ->
            DeviceDetailScreen(
                Routes.decodeIp(entry.arguments?.getString("deviceIp") ?: ""),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NEARBY_WIFI) {
            NearbyNetworksScreen(onBack = { navController.navigate(Routes.DASHBOARD) { launchSingleTop = true } })
        }
        composable(Routes.SECURITY) { SecurityScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.UTILITIES) { UtilitiesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.TOPOLOGY) {
            TopologyScreen(
                onBack = { navController.navigate(Routes.DASHBOARD) { launchSingleTop = true } },
                onDeviceClick = { navController.navigate(Routes.deviceDetail(it)) }
            )
        }
        composable(Routes.WOL) { WolProfilesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CHANNELS) { ChannelAnalyzerScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PUBLIC_IP) { PublicIpScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PROFILES) { NetworkProfilesScreen(onBack = { navController.popBackStack() }) }
    }
}
