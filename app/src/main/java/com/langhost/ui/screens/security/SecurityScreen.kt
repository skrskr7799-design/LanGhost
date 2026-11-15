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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.data.NetworkRepository
import com.langhost.data.SecurityAudit
import com.langhost.data.SecurityFinding
import com.langhost.data.model.WifiNetwork
import com.langhost.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    nearbyNetworks: List<WifiNetwork> = emptyList(),
    nearbyScanCompleted: Boolean = false
) {
    val context = LocalContext.current
    val repo = remember { NetworkRepository.getInstance(context) }
    val devices by repo.devices.collectAsState()
    val network by repo.networkInfo.collectAsState()
    val diff by repo.lastDiff.collectAsState()
    val scanCoverage by repo.scanCoverage.collectAsState()
    val report = remember(devices, network, nearbyNetworks, nearbyScanCompleted, scanCoverage) {
        SecurityAudit.analyze(
            devices = devices,
            currentSsid = network.ssid,
            currentBssid = network.bssid,
            nearby = nearbyNetworks,
            currentSecurity = network.security,
            scanCoverage = scanCoverage,
            nearbyScanCompleted = nearbyScanCompleted
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
