package com.langhost.ui.screens.security

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.NetworkRepository
import com.langhost.data.PrivacyFormatter
import com.langhost.data.SecurityAudit
import com.langhost.data.SecurityFinding
import com.langhost.data.network.NetworkIdentity
import com.langhost.ui.components.ContentPanel
import com.langhost.ui.components.ErrorState
import com.langhost.ui.components.MetricTile
import com.langhost.ui.components.PageHeading
import com.langhost.ui.components.SecurityScoreDial
import com.langhost.ui.components.SectionHeading
import com.langhost.ui.components.StatusBadge
import com.langhost.ui.components.StatusTone
import com.langhost.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val EVIDENCE_FRESHNESS_MS = 5 * 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SecurityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { NetworkRepository.getInstance(context) }
    val appSettings by remember(context) {
        AppSettings(context.applicationContext).settingsFlow
    }.collectAsState(initial = AppSettingsData())
    val scope = rememberCoroutineScope()

    var evidenceNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isWifiScanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repo.refreshNetworkInfo()
        while (true) {
            delay(15_000L)
            evidenceNow = System.currentTimeMillis()
        }
    }

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
    val diff by repo.lastDiff.collectAsState()
    val scanCoverage by repo.scanCoverage.collectAsState()
    val nearbyNetworks by repo.nearbyNetworks.collectAsState()
    val nearbyScanCompleted by repo.nearbyScanCompleted.collectAsState()
    val nearbyScanTime by repo.nearbyScanTime.collectAsState()
    val serviceScanCompleted by repo.securityPortsScanCompleted.collectAsState()
    val serviceScanTime by repo.securityPortsScanTime.collectAsState()
    val isSecurityScanning by repo.isSecurityScanning.collectAsState()
    val isDeviceScanning by repo.isScanning.collectAsState()

    val currentNetworkScope = remember(network) {
        if (network.isConnected) {
            NetworkIdentity.scopeId(network.ssid, network.gateway, network.ipAddress, network.netmask)
        } else ""
    }
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

    val nearbyEvidenceFresh = nearbyScanCompleted && nearbyScanTime > 0L &&
        evidenceNow >= nearbyScanTime && evidenceNow - nearbyScanTime <= EVIDENCE_FRESHNESS_MS
    val isAnyScanRunning = isWifiScanning || isSecurityScanning || isDeviceScanning

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Центр безопасности", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("АНАЛИЗ СЕТИ", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
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
            val expanded = maxWidth >= 840.dp
            LazyColumn(
                modifier = Modifier.widthIn(max = 1180.dp).fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = if (expanded) 28.dp else 16.dp,
                    vertical = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    PageHeading(
                        title = "Центр безопасности",
                        subtitle = "Состояние сети, качество доказательств и приоритетные действия.",
                        eyebrow = "LAN GHOST / БЕЗОПАСНОСТЬ"
                    )
                }

                item {
                    if (expanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            SecurityPosturePanel(
                                score = report.score,
                                isConnected = network.isConnected,
                                ssid = network.ssid,
                                security = network.security,
                                modifier = Modifier.weight(1.15f)
                            )
                            EvidenceSnapshotPanel(
                                deviceComplete = report.deviceScanCompleted,
                                serviceComplete = report.serviceScanCompleted,
                                serviceAdequate = report.serviceEvidenceAdequate,
                                nearbyFresh = nearbyEvidenceFresh,
                                modifier = Modifier.weight(0.85f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SecurityPosturePanel(
                                score = report.score,
                                isConnected = network.isConnected,
                                ssid = network.ssid,
                                security = network.security
                            )
                            EvidenceSnapshotPanel(
                                deviceComplete = report.deviceScanCompleted,
                                serviceComplete = report.serviceScanCompleted,
                                serviceAdequate = report.serviceEvidenceAdequate,
                                nearbyFresh = nearbyEvidenceFresh
                            )
                        }
                    }
                }

                item {
                    SectionHeading(title = "Следующие действия")
                    Spacer(Modifier.height(8.dp))
                    if (expanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SecurityAction(
                                title = if (isDeviceScanning || isSecurityScanning) "Проверка выполняется" else "Устройства и сервисы",
                                description = if (report.serviceScanCompleted) "Обновить сведения об узлах и TCP-портах" else "Найти узлы и проверить выбранные TCP-порты",
                                icon = Icons.Default.Radar,
                                running = isDeviceScanning || isSecurityScanning,
                                enabled = !isAnyScanRunning,
                                onClick = {
                                    scope.launch {
                                        scanError = null
                                        try {
                                            val coverage = repo.scanCoverage.value
                                            val stale = coverage.scannedAt <= 0L ||
                                                System.currentTimeMillis() - coverage.scannedAt > EVIDENCE_FRESHNESS_MS
                                            if (coverage.checkedAddresses <= 0 || stale ||
                                                coverage.networkScope != currentNetworkScope
                                            ) repo.scanDevices()
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
                                modifier = Modifier.weight(1f)
                            )
                            SecurityAction(
                                title = if (isWifiScanning) "Сканирование Wi-Fi" else "Окружение Wi-Fi",
                                description = if (nearbyEvidenceFresh) "Обновить список соседних точек доступа" else "Сравнить режимы защиты соседних точек",
                                icon = Icons.Default.WifiFind,
                                running = isWifiScanning,
                                enabled = !isAnyScanRunning,
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
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecurityAction(
                                title = if (isDeviceScanning || isSecurityScanning) "Проверка выполняется" else "Устройства и сервисы",
                                description = if (report.serviceScanCompleted) "Обновить сведения об узлах и TCP-портах" else "Найти узлы и проверить выбранные TCP-порты",
                                icon = Icons.Default.Radar,
                                running = isDeviceScanning || isSecurityScanning,
                                enabled = !isAnyScanRunning,
                                onClick = {
                                    scope.launch {
                                        scanError = null
                                        try {
                                            val coverage = repo.scanCoverage.value
                                            val stale = coverage.scannedAt <= 0L ||
                                                System.currentTimeMillis() - coverage.scannedAt > EVIDENCE_FRESHNESS_MS
                                            if (coverage.checkedAddresses <= 0 || stale ||
                                                coverage.networkScope != currentNetworkScope
                                            ) repo.scanDevices()
                                            repo.scanSecurityPorts()
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: SecurityException) {
                                            scanError = "Система запретила сетевое сканирование. Проверьте разрешения приложения."
                                        } catch (_: Exception) {
                                            scanError = "Не удалось завершить аудит устройств и портов. Результаты останутся неполными."
                                        }
                                    }
                                }
                            )
                            SecurityAction(
                                title = if (isWifiScanning) "Сканирование Wi-Fi" else "Окружение Wi-Fi",
                                description = if (nearbyEvidenceFresh) "Обновить список соседних точек доступа" else "Сравнить режимы защиты соседних точек",
                                icon = Icons.Default.WifiFind,
                                running = isWifiScanning,
                                enabled = !isAnyScanRunning,
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
                                }
                            )
                        }
                    }
                }

                scanError?.let { message ->
                    item {
                        ErrorState(
                            title = "Операция не завершена",
                            description = message
                        )
                    }
                }

                item {
                    SectionHeading(title = "Покрытие сканирования")
                    Spacer(Modifier.height(8.dp))
                    CoverageAndPortsPanel(
                        checked = scanCoverage.checkedAddresses,
                        total = scanCoverage.totalUsableAddresses,
                        percent = report.coveragePercent,
                        subnet = scanCoverage.subnetCidr,
                        truncated = scanCoverage.truncated,
                        deviceComplete = report.deviceScanCompleted,
                        serviceComplete = report.serviceScanCompleted,
                        auditedTargets = report.auditedTargetCount,
                        openPorts = report.openPortCount,
                        closedPorts = report.closedPortCount,
                        filteredPorts = report.filteredPortCount,
                        unknownPorts = report.unknownPortCount
                    )
                }

                if (report.securityMismatches.isNotEmpty()) {
                    item { SectionHeading(title = "Wi-Fi configuration differences") }
                    items(report.securityMismatches) { accessPoint ->
                        ContentPanel {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Wifi, contentDescription = null, tint = Warning)
                                    Text(accessPoint.ssid, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Spacer(Modifier.weight(1f))
                                    StatusBadge("Проверить", StatusTone.Warning)
                                }
                                Text(
                                    "${PrivacyFormatter.bssid(accessPoint.bssid, appSettings.maskNetworkIdentifiers)} · ${accessPoint.security} · ${accessPoint.signalDbm} dBm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    "Различие конфигурации — повод проверить точку доступа, но не доказательство атаки.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }

                item {
                    SectionHeading(title = "Находки по приоритету", actionLabel = "${report.findings.size} всего")
                }
                if (report.findings.isEmpty()) {
                    item {
                        ContentPanel {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success)
                                Text("Нет находок для отображения.", color = TextSecondary)
                            }
                        }
                    }
                } else {
                    items(report.findings.sortedBy { it.severity.ordinal }) { finding ->
                        FindingCard(finding, devices, appSettings.maskNetworkIdentifiers)
                    }
                }

                diff?.let { result ->
                    if (result.appeared.isNotEmpty() || result.disappeared.isNotEmpty()) {
                        item { SectionHeading(title = "Изменения с прошлого сканирования") }
                        items(result.appeared) { device ->
                            ChangeRow(
                                title = device.displayName,
                                detail = PrivacyFormatter.ip(device.ip, appSettings.maskNetworkIdentifiers),
                                appeared = true
                            )
                        }
                        items(result.disappeared) { device ->
                            ChangeRow(
                                title = device.displayName,
                                detail = PrivacyFormatter.ip(device.ip, appSettings.maskNetworkIdentifiers),
                                appeared = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityPosturePanel(
    score: Int?,
    isConnected: Boolean,
    ssid: String,
    security: String,
    modifier: Modifier = Modifier
) {
    ContentPanel(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("СОСТОЯНИЕ БЕЗОПАСНОСТИ", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                StatusBadge(
                    label = if (isConnected) "Сеть подключена" else "Нет подключения",
                    tone = if (isConnected) StatusTone.Success else StatusTone.Warning
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                SecurityScoreDial(score = score, modifier = Modifier.size(116.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        if (score == null) "Оценка не рассчитана" else "Индекс проверенных признаков",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (score == null) {
                            "Недостаточно свежих и полных доказательств. Это не нулевой балл."
                        } else {
                            "Результат ограничен фактически проверенными данными и не является гарантией безопасности."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            HorizontalDivider(color = Divider)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = AccentCyan)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (ssid.isBlank() || ssid == "—") "Имя сети недоступно" else ssid, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("Режим защиты: ${security.ifBlank { "не определён" }}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun EvidenceSnapshotPanel(
    deviceComplete: Boolean,
    serviceComplete: Boolean,
    serviceAdequate: Boolean,
    nearbyFresh: Boolean,
    modifier: Modifier = Modifier
) {
    ContentPanel(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("КАЧЕСТВО ДОКАЗАТЕЛЬСТВ", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
            Text("Что действительно проверено", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            EvidenceLine(
                title = "Обнаружение устройств",
                detail = if (deviceComplete) "Свежий результат для текущей сети" else "Нет свежего полного результата",
                complete = deviceComplete
            )
            EvidenceLine(
                title = "Проверка TCP-сервисов",
                detail = when {
                    !serviceComplete -> "Не выполнена или устарела"
                    serviceAdequate -> "Все ожидаемые результаты подтверждены"
                    else -> "Есть неопределённые результаты"
                },
                complete = serviceComplete && serviceAdequate
            )
            EvidenceLine(
                title = "Окружение Wi-Fi",
                detail = if (nearbyFresh) "Соседние точки проверены недавно" else "Нет свежего сканирования",
                complete = nearbyFresh
            )
            Text(
                "Состояния «неизвестно», «таймаут» и «не проверено» не трактуются как безопасные.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun EvidenceLine(title: String, detail: String, complete: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (complete) Icons.Default.CheckCircle else Icons.Default.Schedule,
            contentDescription = null,
            tint = if (complete) Success else Warning,
            modifier = Modifier.size(19.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        StatusBadge(if (complete) "Готово" else "Нужно", if (complete) StatusTone.Success else StatusTone.Warning)
    }
}

@Composable
private fun SecurityAction(
    title: String,
    description: String,
    icon: ImageVector,
    running: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Border.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextTertiary)
            }
        }
    }
}

@Composable
private fun CoverageAndPortsPanel(
    checked: Int,
    total: Long,
    percent: Int,
    subnet: String,
    truncated: Boolean,
    deviceComplete: Boolean,
    serviceComplete: Boolean,
    auditedTargets: Int,
    openPorts: Int,
    closedPorts: Int,
    filteredPorts: Int,
    unknownPorts: Int
) {
    ContentPanel {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("ОБНАРУЖЕНИЕ IPV4", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text(
                        if (total > 0L) "$checked / $total адресов" else "Диапазон не определён",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                StatusBadge(
                    label = when {
                        !deviceComplete -> "Нет данных"
                        truncated -> "Частично"
                        else -> "$percent%"
                    },
                    tone = when {
                        !deviceComplete -> StatusTone.Warning
                        truncated -> StatusTone.Warning
                        else -> StatusTone.Success
                    }
                )
            }
            if (total > 0L) {
                LinearProgressIndicator(
                    progress = { (percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = if (truncated) Warning else Success,
                    trackColor = SurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (subnet.isNotBlank()) "Подсеть $subnet" else "Подсеть не определена", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (truncated) Text("Диапазон ограничен", color = Warning, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = Divider)
            Text("АУДИТ TCP-СЕРВИСОВ", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Узлы", if (serviceComplete) auditedTargets.toString() else "—", accent = AccentCyan, modifier = Modifier.weight(1f))
                MetricTile("Открыто", if (serviceComplete) openPorts.toString() else "—", accent = Warning, modifier = Modifier.weight(1f))
                MetricTile("Закрыто", if (serviceComplete) closedPorts.toString() else "—", accent = Success, modifier = Modifier.weight(1f))
            }
            if (serviceComplete) {
                Text(
                    "Фильтрация / таймаут: $filteredPorts · неизвестно: $unknownPorts",
                    color = if (filteredPorts > 0 || unknownPorts > 0) Warning else TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    "Статистика портов появится после свежей проверки сервисов.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FindingCard(
    finding: SecurityFinding,
    devices: List<com.langhost.data.model.Device>,
    maskIdentifiers: Boolean
) {
    val safeDetail = if (maskIdentifiers) {
        devices.fold(finding.detail) { text, device ->
            if (device.ip.isBlank()) text
            else text.replace(device.ip, PrivacyFormatter.ip(device.ip, true))
        }
    } else finding.detail

    val tone = when (finding.severity) {
        SecurityFinding.Severity.HIGH -> StatusTone.Danger
        SecurityFinding.Severity.MEDIUM -> StatusTone.Warning
        SecurityFinding.Severity.LOW -> StatusTone.Info
        SecurityFinding.Severity.INFO -> StatusTone.Neutral
    }
    val tint = when (finding.severity) {
        SecurityFinding.Severity.HIGH -> Danger
        SecurityFinding.Severity.MEDIUM -> Warning
        SecurityFinding.Severity.LOW -> Secondary
        SecurityFinding.Severity.INFO -> Primary
    }
    val severityLabel = when (finding.severity) {
        SecurityFinding.Severity.HIGH -> "Высокий"
        SecurityFinding.Severity.MEDIUM -> "Средний"
        SecurityFinding.Severity.LOW -> "Низкий"
        SecurityFinding.Severity.INFO -> "Информация"
    }

    ContentPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(finding.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    StatusBadge(severityLabel, tone)
                }
                Text(safeDetail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ChangeRow(title: String, detail: String, appeared: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (appeared) Icons.Default.AddCircle else Icons.Default.RemoveCircle,
            contentDescription = null,
            tint = if (appeared) Success else Danger
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
