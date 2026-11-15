package com.langhost.ui.screens.dashboard

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.langhost.ui.components.ContentPanel
import com.langhost.ui.components.MetricTile
import com.langhost.ui.components.PageHeading
import com.langhost.ui.components.SectionHeading
import com.langhost.ui.components.StatusBadge
import com.langhost.ui.components.StatusTone
import com.langhost.ui.theme.Background
import com.langhost.ui.theme.Border
import com.langhost.ui.theme.Divider
import com.langhost.ui.theme.Primary
import com.langhost.ui.theme.Surface
import com.langhost.ui.theme.SurfaceVariant
import com.langhost.ui.theme.TextPrimary
import com.langhost.ui.theme.TextSecondary
import com.langhost.ui.theme.TextTertiary
import com.langhost.ui.theme.Warning
import com.langhost.ui.theme.Success
import com.langhost.ui.theme.AccentCyan
import com.langhost.ui.theme.OnPrimary
import com.langhost.ui.theme.LanGhostSpacing

@OptIn(ExperimentalPermissionsApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            "LanGhost",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "NETWORK INTELLIGENCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshNetworkInfo() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = TextSecondary)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LanGhostSpacing.md, vertical = LanGhostSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LanGhostSpacing.xl)
        ) {
            PageHeading(
                title = "Обзор сети",
                subtitle = "Состояние и активность вашей локальной сети",
                eyebrow = "LIVE OVERVIEW"
            )

            if (!permissionState.allPermissionsGranted) {
                PermissionNotice(onRequest = { permissionState.launchMultiplePermissionRequest() })
            }

            NetworkOverview(
                isConnected = network.isConnected,
                ssid = network.ssid,
                gateway = network.gateway,
                ipAddress = network.ipAddress,
                channel = network.channel
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.sm)
            ) {
                MetricTile(
                    label = "Устройства онлайн",
                    value = if (state.devices.isEmpty() && state.lastScanTime == 0L) "—"
                    else state.devices.count { it.isOnline }.toString(),
                    detail = if (state.devices.isEmpty() && state.lastScanTime == 0L) "Ещё не сканировали"
                    else "из ${state.devices.size} обнаруженных",
                    accent = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Уровень сигнала",
                    value = if (network.signalDbm != 0) "${network.signalDbm}" else "—",
                    detail = if (network.signalDbm != 0) "dBm · Wi-Fi" else "Нет данных",
                    accent = Primary,
                    modifier = Modifier.weight(1f)
                )
            }

            ScanControl(
                isScanning = state.isScanning,
                hasScanned = state.lastScanTime > 0L,
                onScan = { viewModel.startScan() }
            )

            if (state.scanCoverage.scannedAt > 0L) {
                CoveragePanel(
                    checked = state.scanCoverage.checkedAddresses,
                    total = state.scanCoverage.totalUsableAddresses,
                    percent = state.scanCoverage.coveragePercent,
                    subnet = state.scanCoverage.subnetCidr,
                    truncated = state.scanCoverage.truncated
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(LanGhostSpacing.md)) {
                SectionHeading(title = "Быстрый доступ")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.sm)
                ) {
                    ShortcutTile("Устройства", "Инвентарь сети", Icons.Default.Devices, onNavigateToDevices, Modifier.weight(1f))
                    ShortcutTile("Топология", "Связи и узлы", Icons.Default.Hub, onNavigateToTopology, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.sm)
                ) {
                    ShortcutTile("Безопасность", "Аудит сети", Icons.Default.Shield, onNavigateToSecurity, Modifier.weight(1f))
                    ShortcutTile("Wi-Fi вокруг", "Соседние сети", Icons.Default.WifiFind, onNavigateToNearbyWifi, Modifier.weight(1f))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(LanGhostSpacing.md)) {
                SectionHeading(title = "Инструменты")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.sm)
                ) {
                    ShortcutTile("Каналы", "Анализ эфира", Icons.Default.BarChart, onNavigateToChannels, Modifier.weight(1f))
                    ShortcutTile("Публичный IP", "Внешняя сеть", Icons.Default.Public, onNavigateToPublicIp, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.sm)
                ) {
                    ShortcutTile("Профили", "Сохранённые сети", Icons.Default.Folder, onNavigateToProfiles, Modifier.weight(1f))
                    ShortcutTile("Wake-on-LAN", "Пробуждение ПК", Icons.Default.Power, onNavigateToWol, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.sm)
                ) {
                    ShortcutTile("Диагностика", "Ping, DNS и другое", Icons.Default.Build, onNavigateToUtilities, Modifier.weight(1f))
                    ShortcutTile("Настройки", "Приложение и мониторинг", Icons.Default.Settings, onNavigateToSettings, Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(LanGhostSpacing.xs))
        }
    }
}

@Composable
private fun NetworkOverview(
    isConnected: Boolean,
    ssid: String,
    gateway: String,
    ipAddress: String,
    channel: Int
) {
    ContentPanel {
        Column(verticalArrangement = Arrangement.spacedBy(LanGhostSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "ТЕКУЩЕЕ ПОДКЛЮЧЕНИЕ",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                StatusBadge(
                    label = if (isConnected) "Подключено" else "Нет связи",
                    tone = if (isConnected) StatusTone.Success else StatusTone.Warning
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.md)
            ) {
                androidx.compose.material3.Surface(
                    color = if (isConnected) Primary.copy(alpha = 0.14f) else SurfaceVariant,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (isConnected) Primary else TextTertiary,
                        modifier = Modifier.padding(14.dp).size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (isConnected && ssid.isNotBlank()) ssid else "Нет активной сети",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isConnected) "Локальное сетевое окружение" else "Подключитесь к Wi-Fi или Ethernet",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            HorizontalDivider(color = Divider)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.md)
            ) {
                NetworkFact("IP-АДРЕС", ipAddress, Modifier.weight(1f))
                NetworkFact("ШЛЮЗ", gateway, Modifier.weight(1f))
                NetworkFact("КАНАЛ", if (channel > 0) channel.toString() else "—", Modifier.weight(0.65f))
            }
        }
    }
}

@Composable
private fun NetworkFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ScanControl(
    isScanning: Boolean,
    hasScanned: Boolean,
    onScan: () -> Unit
) {
    Button(
        onClick = onScan,
        enabled = !isScanning,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            contentColor = OnPrimary,
            disabledContainerColor = Primary.copy(alpha = 0.5f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = OnPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Radar, contentDescription = null)
            }
            Text(
                text = when {
                    isScanning -> "Сканирование сети…"
                    hasScanned -> "Обновить сканирование"
                    else -> "Просканировать сеть"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CoveragePanel(
    checked: Long,
    total: Long,
    percent: Int,
    subnet: String,
    truncated: Boolean
) {
    ContentPanel {
        Column(verticalArrangement = Arrangement.spacedBy(LanGhostSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("ПОКРЫТИЕ СКАНИРОВАНИЯ", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        if (total > 0L) "$checked из $total адресов" else "Диапазон не определён",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                StatusBadge(
                    label = if (truncated) "Частично" else "$percent%",
                    tone = if (truncated) StatusTone.Warning else StatusTone.Success
                )
            }
            if (total > 0L) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                    color = if (truncated) Warning else Success,
                    trackColor = SurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (subnet.isNotBlank()) "Подсеть $subnet" else "IPv4",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                if (truncated) {
                    Text("Лимит диапазона", style = MaterialTheme.typography.bodySmall, color = Warning)
                }
            }
        }
    }
}

@Composable
private fun PermissionNotice(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Warning.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = Warning)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Нужны разрешения", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Для чтения SSID и сканирования Wi-Fi нужны системные разрешения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            androidx.compose.material3.TextButton(onClick = onRequest) {
                Text("Разрешить", color = Warning)
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.material3.Surface(
                color = Primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.padding(9.dp).size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 1
                )
            }
        }
    }
}
