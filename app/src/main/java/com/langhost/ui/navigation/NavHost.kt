package com.langhost.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.langhost.ui.screens.dashboard.DashboardScreen
import com.langhost.ui.screens.device.DeviceDetailScreen
import com.langhost.ui.screens.devices.DevicesScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val DEVICES = "devices"
    const val DEVICE_DETAIL = "device/{deviceIp}"

    fun deviceDetail(ip: String) = "device/$ip"
}

@Composable
fun LanGhostNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToDevices = {
                    navController.navigate(Routes.DEVICES)
                }
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
            val ip = backStackEntry.arguments?.getString("deviceIp") ?: ""
            DeviceDetailScreen(
                deviceIp = ip,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
