package com.langhost.ui.screens.dashboard

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.langhost.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DashboardScreen(
    onNavigateToDevices: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNearbyWifi: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToUtilities: () -> Unit = {},
    onNavigateToTopology: () -> Unit = {},
    onNavigateToWol: () -> Unit = {},
    onNavigateToChannels: () -> Unit = {},
    onNavigateToPublicIp: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val network = state.networkInfo
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    val permissionState = rememberMultiplePermissionsState(permissions)
    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) permissionState.launchMultiplePermissionRequest()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("LanGhost", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refreshNetworkInfo() }) { Icon(Icons.Default.Refresh, "Обновить", tint = TextPrimary) }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Настройки", tint = TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!permissionState.allPermissionsGranted) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.15f)), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Warning, null, tint = Warning)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Нужны разрешения", color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("Для SSID и сканирования нужна геолокация.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        TextButton(onClick = { permissionState.launchMultiplePermissionRequest() }) { Text("Разрешить") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Wifi, null, tint = if (network.isConnected) Primary else TextTertiary)
                        Text(if (network.isConnected) network.ssid else "Нет подключения", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = Divider)
                    InfoRow("Канал", if (network.channel > 0) network.channel.toString() else "—")
                    InfoRow("Шлюз", network.gateway)
                    InfoRow("IP", network.ipAddress)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Онлайн", state.devices.count { it.isOnline }.let { if (state.devices.isNotEmpty()) it.toString() else "—" }, Icons.Default.Devices, Modifier.weight(1f))
                StatCard("Сигнал", if (network.signalDbm != 0) "${network.signalDbm} dBm" else "—", Icons.Default.Wifi, Modifier.weight(1f))
            }

            AnimatedVisibility(visible = state.isScanning) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceVariant), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        Text("Сканирование...", color = TextPrimary)
                    }
                }
            }

            Text("Действия", style = MaterialTheme.typography.titleMedium, color = TextSecondary)

            ActionButton("Сканировать", if (state.lastScanTime > 0) "Готово" else "Найти хосты", Icons.Default.Radar, { viewModel.startScan() }, !state.isScanning)
            ActionButton("Устройства", if (state.devices.isNotEmpty()) "${state.devices.count { it.isOnline }} / ${state.devices.size}" else "Список", Icons.Default.Devices, onNavigateToDevices)
            ActionButton("Топология", "Карта сети", Icons.Default.Hub, onNavigateToTopology)
            ActionButton("Каналы Wi-Fi", "Загруженность", Icons.Default.BarChart, onNavigateToChannels)
            ActionButton("Внешняя сеть", "Публичный IP", Icons.Default.Public, onNavigateToPublicIp)
            ActionButton("Профили сетей", "Дом / Работа / Кафе", Icons.Default.Folder, onNavigateToProfiles)
            ActionButton("Аудит безопасности", "Оценка сети", Icons.Default.Shield, onNavigateToSecurity)
            ActionButton("Соседние сети", "Wi-Fi вокруг", Icons.Default.WifiFind, onNavigateToNearbyWifi)
            ActionButton("WoL профили", "Пробуждение", Icons.Default.Power, onNavigateToWol)
            ActionButton("Утилиты", "Ping, DNS", Icons.Default.Build, onNavigateToUtilities)
            ActionButton("Настройки", "Блокировка, отчёт, Watch", Icons.Default.Settings, onNavigateToSettings)
        }
    }
}

@Composable private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable private fun ActionButton(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface, disabledContainerColor = Surface.copy(alpha = 0.6f)), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary)
        }
    }
}
