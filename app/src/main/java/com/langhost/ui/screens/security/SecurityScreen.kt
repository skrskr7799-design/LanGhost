package com.langhost.ui.screens.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.Manifest
import android.os.Build
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.data.NetworkRepository
import com.langhost.data.SecurityAudit
import com.langhost.data.SecurityFinding
import com.langhost.data.network.NetworkIdentity
import com.langhost.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SecurityScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { NetworkRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    var evidenceNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        repo.refreshNetworkInfo()
        while (true) {
            delay(15_000L)
            evidenceNow = System.currentTimeMillis()
        }
    }
    var isWifiScanning by remember { mutableStateOf(false) }
    val wifiPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val wifiPermissionState = rememberMultiplePermissionsState(wifiPermissions)
    val devices by repo.devices.collectAsState()
    val network by repo.networkInfo.collectAsState()
    val currentNetworkScope = remember(network) {
        if (network.isConnected) {
            NetworkIdentity.scopeId(network.ssid, network.gateway, network.ipAddress, network.netmask)
        } else {
            ""
        }
    }
    val diff by repo.lastDiff.collectAsState()
    val scanCoverage by repo.scanCoverage.collectAsState()
    val nearbyNetworks by repo.nearbyNetworks.collectAsState()
    val nearbyScanCompleted by repo.nearbyScanCompleted.collectAsState()
    val nearbyScanTime by repo.nearbyScanTime.collectAsState()
    val serviceScanCompleted by repo.securityPortsScanCompleted.collectAsState()
    val serviceScanTime by repo.securityPortsScanTime.collectAsState()
    val isSecurityScanning by repo.isSecurityScanning.collectAsState()
    val isDeviceScanning by repo.isScanning.collectAsState()
    val report = remember(
        devices, network, currentNetworkScope, nearbyNetworks, nearbyScanCompleted,
        nearbyScanTime, scanCoverage, serviceScanCompleted, serviceScanTime, evidenceNow
    ) {
        SecurityAudit.analyze(
            devices = devices,
            currentSsid = network.ssid,
            currentBssid = network.bssid,
            nearby = nearbyNetworks,
            currentSecurity = network.security,
            scanCoverage = scanCoverage,
            nearbyScanCompleted = nearbyScanCompleted,
            serviceScanCompleted = serviceScanCompleted,
            currentNetworkScope = currentNetworkScope,
            nearbyScanTime = nearbyScanTime,
            serviceScanTime = serviceScanTime,
            now = evidenceNow
        )
    }
    val score = report.score

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аудит безопасности", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Оценка проверенных признаков", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        Text(
                            text = score?.toString() ?: "—",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                score == null -> TextTertiary
                                score >= 80 -> Success
                                score >= 50 -> Secondary
                                else -> Danger
                            }
                        )
                        Text(
                            text = if (score == null) "недостаточно данных" else "из 100",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                        if (report.deviceScanCompleted) {
                            Text(
                                "Покрытие подсети: ${report.coveragePercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (!wifiPermissionState.allPermissionsGranted) {
                                    wifiPermissionState.launchMultiplePermissionRequest()
                                } else {
                                    scope.launch {
                                        isWifiScanning = true
                                        try {
                                            repo.scanNearbyNetworks()
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: SecurityException) {
                                            // Permissions may be revoked after the UI check.
                                        } catch (_: Exception) {
                                            // A platform scan may fail or be throttled.
                                        } finally {
                                            isWifiScanning = false
                                        }
                                    }
                                }
                            },
                            enabled = !isWifiScanning
                        ) {
                            if (isWifiScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (isWifiScanning) "Сканирование Wi-Fi…" else "Проверить соседние сети")
                        }
                        if (nearbyScanCompleted && nearbyScanTime > 0L) {
                            Text(
                                "Последнее свежее сканирование: " +
                                    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                        .format(java.util.Date(nearbyScanTime)),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val coverage = repo.scanCoverage.value
                                        val stale = coverage.scannedAt <= 0L ||
                                            System.currentTimeMillis() - coverage.scannedAt > 5 * 60 * 1000L
                                        if (coverage.checkedAddresses <= 0 || stale) {
                                            repo.scanDevices()
                                        }
                                        repo.scanSecurityPorts()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        // The report stays incomplete if a platform scan fails.
                                    }
                                }
                            },
                            enabled = !isSecurityScanning && !isDeviceScanning
                        ) {
                            if (isSecurityScanning || isDeviceScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (isSecurityScanning || isDeviceScanning) "Проверка устройств и сервисов…"
                                else "Проверить устройства и сервисы"
                            )
                        }
                        Text(
                            when {
                                !report.serviceScanCompleted -> "Проверка сервисов ещё не выполнена или устарела"
                                report.serviceEvidenceAdequate -> "Проверка сервисов завершена · результаты подтверждены"
                                else -> "Проверка завершена, но часть результатов неопределённа"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (report.serviceEvidenceAdequate) Success else Warning
                        )
                        if (report.serviceScanCompleted) {
                            Text(
                                "Порты: открыто ${report.openPortCount} · закрыто ${report.closedPortCount} · фильтрация/таймаут ${report.filteredPortCount} · неизвестно ${report.unknownPortCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            if (report.securityMismatches.isNotEmpty()) {
                item {
                    Text("Различия в защите точек доступа", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                }
                items(report.securityMismatches) { accessPoint ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(accessPoint.ssid, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(
                                "${accessPoint.bssid} · ${accessPoint.security} · ${accessPoint.signalDbm} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                "Это сигнал для проверки конфигурации, а не подтверждение атаки.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Text("Находки", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            }
            items(report.findings) { finding ->
                FindingCard(finding)
            }

            diff?.let { result ->
                if (result.appeared.isNotEmpty() || result.disappeared.isNotEmpty()) {
                    item {
                        Text("Изменения с прошлого скана", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    }
                    items(result.appeared) { device ->
                        Text("+ ${device.displayName} (${device.ip})", color = Success, style = MaterialTheme.typography.bodyMedium)
                    }
                    items(result.disappeared) { device ->
                        Text("- ${device.displayName} (${device.ip})", color = Danger, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingCard(finding: SecurityFinding) {
    val color = when (finding.severity) {
        SecurityFinding.Severity.HIGH -> Danger
        SecurityFinding.Severity.MEDIUM -> Warning
        SecurityFinding.Severity.LOW -> Secondary
        SecurityFinding.Severity.INFO -> Primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = color)
            Column {
                Text(finding.title, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(finding.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
