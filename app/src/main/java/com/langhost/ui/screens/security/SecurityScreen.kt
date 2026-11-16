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
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.PrivacyFormatter
import com.langhost.data.SecurityAudit
import com.langhost.data.SecurityFinding
import com.langhost.data.network.NetworkIdentity
import com.langhost.ui.components.SecurityScoreDial
import com.langhost.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SecurityScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { NetworkRepository.getInstance(context) }
    val appSettings by remember(context) { AppSettings(context.applicationContext).settingsFlow }.collectAsState(initial = AppSettingsData())
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
    var scanError by remember { mutableStateOf<String?>(null) }
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
                title = { Text("Аудит безопасности", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
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
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SECURITY POSTURE", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                        SecurityScoreDial(score = score, modifier = Modifier.size(132.dp))
                        Text(
                            text = if (score == null) "Оценка пока недоступна" else "Итог по проверенным признакам",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (score == null) "Нужны свежие и полные данные по сети, сервисам и Wi-Fi." else "Результат ограничен фактически проверенными данными.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
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
                                        scanError = null
                                        try {
                                            repo.scanNearbyNetworks()
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: SecurityException) {
                                            scanError = "Нет разрешения на сканирование Wi-Fi. Проверьте доступ к геолокации и ближайшим устройствам."
                                        } catch (_: Exception) {
                                            scanError = "Не удалось завершить сканирование Wi-Fi. Проверьте разрешения и повторите попытку."
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
                                    scanError = null
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
                                    } catch (_: SecurityException) {
                                        scanError = "Система запретила сетевое сканирование. Проверьте разрешения приложения."
                                    } catch (_: Exception) {
                                        scanError = "Не удалось завершить аудит устройств и портов. Результаты останутся неполными."
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
                                "Удалённых устройств проверено: ${report.auditedTargetCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                "Порты: открыто ${report.openPortCount} · закрыто ${report.closedPortCount} · фильтрация/таймаут ${report.filteredPortCount} · неизвестно ${report.unknownPortCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            scanError?.let { message ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.10f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Danger)
                            Text(message, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
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
                                "${PrivacyFormatter.bssid(accessPoint.bssid, appSettings.maskNetworkIdentifiers)} · ${accessPoint.security} · ${accessPoint.signalDbm} dBm",
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
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.75f))
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
