package com.langhost.ui.screens.dashboard

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.PrivacyFormatter
import com.langhost.data.model.DiscoverySourceStatus
import com.langhost.ui.components.ContentPanel
import com.langhost.ui.components.DataNoticeTone
import com.langhost.ui.components.DataQualityNotice
import com.langhost.ui.components.MetricTile
import com.langhost.ui.components.NetworkOrbit
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
import com.langhost.ui.theme.TechnicalTypography
import com.langhost.ui.theme.AmbientBlue

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
    onNavigateToTools: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettings by androidx.compose.runtime.remember(context) { AppSettings(context.applicationContext).settingsFlow }.collectAsState(initial = AppSettingsData())
    val network = state.networkInfo
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NetworkOrbit(isConnected = network.isConnected, modifier = Modifier.size(30.dp))
                            Text(
                                "LanGhost",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
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
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWideLayout = maxWidth >= 840.dp
            Column(
                modifier = Modifier
                    .widthIn(max = 1120.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AmbientBlue.copy(alpha = 0.22f), Background),
                            radius = 920f
                        )
                    )
                    .padding(horizontal = if (isWideLayout) 32.dp else LanGhostSpacing.md, vertical = LanGhostSpacing.lg),
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
            if (!network.isConnected) {
                DataQualityNotice(
                    title = "Нет активного сетевого подключения",
                    description = "Сведения о локальной сети и результаты новых проверок могут быть недоступны. Подключитесь к Wi-Fi или Ethernet и обновите обзор.",
                    tone = DataNoticeTone.Warning
                )
            } else if (
                state.lastScanTime > 0L &&
                System.currentTimeMillis() >= state.lastScanTime &&
                System.currentTimeMillis() - state.lastScanTime > 5 * 60 * 1000L
            ) {
                DataQualityNotice(
                    title = "Инвентарь требует обновления",
                    description = "Последнее сканирование было более пяти минут назад. Используйте обновление, чтобы проверить текущее состояние устройств.",
                    tone = DataNoticeTone.Info
                )
            }

            if (isWideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.xl),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.spacedBy(LanGhostSpacing.lg)
                    ) {
                        NetworkOverview(
                            isConnected = network.isConnected,
                            ssid = network.ssid,
                            gateway = PrivacyFormatter.ip(network.gateway, appSettings.maskNetworkIdentifiers),
                            ipAddress = PrivacyFormatter.ip(network.ipAddress, appSettings.maskNetworkIdentifiers),
                            channel = network.channel
                        )
                        DashboardMetrics(
                            totalDevices = state.devices.size,
                            onlineDevices = if (state.devices.isEmpty() && state.lastScanTime == 0L) null
                                else state.devices.count { it.isOnline },
                            signalDbm = network.signalDbm
                        )
                        if (state.scanCoverage.scannedAt > 0L) {
                            CoveragePanel(
                                checked = state.scanCoverage.checkedAddresses,
                                total = state.scanCoverage.totalUsableAddresses,
                                percent = state.scanCoverage.coveragePercent,
                                subnet = state.scanCoverage.subnetCidr,
                                truncated = state.scanCoverage.truncated,
                                arpStatus = state.scanCoverage.arpStatus,
                                tcpStatus = state.scanCoverage.tcpStatus,
                                dnsSdStatus = state.scanCoverage.dnsSdStatus,
                                ssdpStatus = state.scanCoverage.ssdpStatus,
                                reachabilityStatus = state.scanCoverage.reachabilityStatus
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(0.85f),
                        verticalArrangement = Arrangement.spacedBy(LanGhostSpacing.lg)
                    ) {
                        ScanControl(
                            isScanning = state.isScanning,
                            hasScanned = state.lastScanTime > 0L,
                            permissionGranted = permissionState.allPermissionsGranted,
                            onScan = {
                                if (permissionState.allPermissionsGranted) viewModel.startScan()
                                else permissionState.launchMultiplePermissionRequest()
                            }
                        )
                        WorkspaceShortcuts(
                            onDevices = onNavigateToDevices,
                            onTopology = onNavigateToTopology,
                            onSecurity = onNavigateToSecurity,
                            onTools = onNavigateToTools
                        )
                    }
                }
            } else {
                NetworkOverview(
                    isConnected = network.isConnected,
                    ssid = network.ssid,
                    gateway = PrivacyFormatter.ip(network.gateway, appSettings.maskNetworkIdentifiers),
                    ipAddress = PrivacyFormatter.ip(network.ipAddress, appSettings.maskNetworkIdentifiers),
                    channel = network.channel
                )
                DashboardMetrics(
                    totalDevices = state.devices.size,
                    onlineDevices = if (state.devices.isEmpty() && state.lastScanTime == 0L) null
                        else state.devices.count { it.isOnline },
                    signalDbm = network.signalDbm
                )
                ScanControl(
                    isScanning = state.isScanning,
                    hasScanned = state.lastScanTime > 0L,
                    permissionGranted = permissionState.allPermissionsGranted,
                    onScan = {
                        if (permissionState.allPermissionsGranted) viewModel.startScan()
                        else permissionState.launchMultiplePermissionRequest()
                    }
                )
                if (state.scanCoverage.scannedAt > 0L) {
                    CoveragePanel(
                        checked = state.scanCoverage.checkedAddresses,
                        total = state.scanCoverage.totalUsableAddresses,
                        percent = state.scanCoverage.coveragePercent,
                        subnet = state.scanCoverage.subnetCidr,
                        truncated = state.scanCoverage.truncated,
                        arpStatus = state.scanCoverage.arpStatus,
                        tcpStatus = state.scanCoverage.tcpStatus,
                        dnsSdStatus = state.scanCoverage.dnsSdStatus,
                        ssdpStatus = state.scanCoverage.ssdpStatus,
                        reachabilityStatus = state.scanCoverage.reachabilityStatus
                    )
                }
                WorkspaceShortcuts(
                    onDevices = onNavigateToDevices,
                    onTopology = onNavigateToTopology,
                    onSecurity = onNavigateToSecurity,
                    onTools = onNavigateToTools
                )
            }
            Spacer(modifier = Modifier.height(LanGhostSpacing.xs))
            }
        }
    }
}

@Composable
private fun DashboardMetrics(
    totalDevices: Int,
    onlineDevices: Int?,
    signalDbm: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.sm)
    ) {
        MetricTile(
            label = "Устройства онлайн",
            value = onlineDevices?.toString() ?: "—",
            detail = if (onlineDevices == null) "Ещё не сканировали" else "из $totalDevices обнаруженных",
            accent = AccentCyan,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            label = "Уровень сигнала",
            value = if (signalDbm != 0) "$signalDbm" else "—",
            detail = if (signalDbm != 0) "dBm · Wi-Fi" else "Нет данных",
            accent = Primary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WorkspaceShortcuts(
    onDevices: () -> Unit,
    onTopology: () -> Unit,
    onSecurity: () -> Unit,
    onTools: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LanGhostSpacing.md)) {
        SectionHeading(title = "Рабочие области")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.sm)
        ) {
            ShortcutTile("Устройства", "Инвентарь и активность", Icons.Default.Devices, onDevices, Modifier.weight(1f))
            ShortcutTile("Топология", "Структура локальной сети", Icons.Default.Hub, onTopology, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LanGhostSpacing.sm)
        ) {
            ShortcutTile("Безопасность", "Состояние и аудит", Icons.Default.Shield, onSecurity, Modifier.weight(1f))
            ShortcutTile("Инструменты", "Полный набор диагностики", Icons.Default.Build, onTools, Modifier.weight(1f))
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
                NetworkOrbit(
                    isConnected = isConnected,
                    modifier = Modifier.size(104.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (isConnected && ssid.isNotBlank()) ssid else "Нет активной сети",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
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
            style = TechnicalTypography,
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
    permissionGranted: Boolean,
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
                    !permissionGranted -> "Разрешить доступ для сканирования"
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
    checked: Int,
    total: Long,
    percent: Int,
    subnet: String,
    truncated: Boolean,
    arpStatus: DiscoverySourceStatus,
    tcpStatus: DiscoverySourceStatus,
    dnsSdStatus: DiscoverySourceStatus,
    ssdpStatus: DiscoverySourceStatus,
    reachabilityStatus: DiscoverySourceStatus
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
            HorizontalDivider(color = Border.copy(alpha = 0.55f))
            Text("ДИАГНОСТИКА ИСТОЧНИКОВ", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            SourceStatusRow("ARP-таблица", arpStatus)
            SourceStatusRow("TCP-проверка", tcpStatus)
            SourceStatusRow("mDNS / DNS-SD", dnsSdStatus)
            SourceStatusRow("SSDP / UPnP", ssdpStatus)
            SourceStatusRow("IP reachability", reachabilityStatus)
        }
    }
}

@Composable
private fun SourceStatusRow(label: String, status: DiscoverySourceStatus) {
    val value = discoverySourceStatusLabel(status)
    val tone = when (status) {
        DiscoverySourceStatus.RESPONDED -> Success
        DiscoverySourceStatus.PARTIAL, DiscoverySourceStatus.NO_RESPONSES, DiscoverySourceStatus.UNAVAILABLE -> Warning
        DiscoverySourceStatus.NOT_RUN, DiscoverySourceStatus.NOT_REPORTED -> TextTertiary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = tone, fontWeight = FontWeight.Medium)
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
