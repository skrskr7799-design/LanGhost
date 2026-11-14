package com.langhost.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.langhost.ui.screens.dashboard.DashboardScreen
import com.langhost.ui.screens.device.DeviceDetailScreen
import com.langhost.ui.screens.devices.DevicesScreen
import com.langhost.ui.theme.Divider

object Routes {
    const val DASHBOARD = "dashboard"
    const val DEVICES = "devices"
    const val DEVICE_DETAIL = "device/{deviceIp}"

    // Replace dots so Navigation Compose doesn't break on IP addresses
    fun deviceDetail(ip: String) = "device/${ip.replace('.', '_')}"
    fun decodeIp(encoded: String) = encoded.replace('_', '.')
}

@Composable
fun LanGhostNavHost(
    windowSizeClass: WindowSizeClass? = null
) {
    val isExpanded = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded

    if (isExpanded) {
        TabletNavHost()
    } else {
        PhoneNavHost()
    }
}

@Composable
private fun PhoneNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToDevices = { navController.navigate(Routes.DEVICES) }
            )
        }
        composable(Routes.DEVICES) {
            DevicesScreen(
                onBack = { navController.popBackStack() },
                onDeviceClick = { ip ->
                    navController.navigate(Routes.deviceDetail(ip))
                }
            )
        }
        composable(
            route = Routes.DEVICE_DETAIL,
            arguments = listOf(navArgument("deviceIp") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedIp = backStackEntry.arguments?.getString("deviceIp") ?: ""
            val ip = Routes.decodeIp(encodedIp)
            DeviceDetailScreen(
                deviceIp = ip,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun TabletNavHost() {
    var selectedDeviceIp by remember { mutableStateOf<String?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        DevicesScreen(
            onBack = { selectedDeviceIp = null },
            onDeviceClick = { ip -> selectedDeviceIp = ip },
            modifier = Modifier.weight(0.4f)
        )

        VerticalDivider(color = Divider, thickness = 1.dp)

        if (selectedDeviceIp != null) {
            DeviceDetailScreen(
                deviceIp = selectedDeviceIp!!,
                onBack = { selectedDeviceIp = null },
                modifier = Modifier.weight(0.6f)
            )
        } else {
            DashboardScreen(
                onNavigateToDevices = {},
                modifier = Modifier.weight(0.6f)
            )
        }
    }
}
