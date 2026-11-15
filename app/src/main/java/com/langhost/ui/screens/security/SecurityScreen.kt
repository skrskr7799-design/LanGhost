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
    nearbyNetworks: List<WifiNetwork> = emptyList()
) {
    val context = LocalContext.current
    val repo = remember { NetworkRepository.getInstance(context) }
    val devices by repo.devices.collectAsState()
    val network by repo.networkInfo.collectAsState()
    val diff by repo.lastDiff.collectAsState()

    val report = remember(devices, network, nearbyNetworks) {
        SecurityAudit.analyze(devices, network.ssid, network.bssid, nearbyNetworks)
    }

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
                    Column(Modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Оценка сети", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        Text(
                            text = "${report.score}",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                report.score >= 80 -> Success
                                report.score >= 50 -> Secondary
                                else -> Danger
                            }
                        )
                        Text("из 100", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    }
                }
            }

            if (report.evilTwins.isNotEmpty()) {
                item {
                    Text("Evil Twin", style = MaterialTheme.typography.titleMedium, color = Danger)
                }
                items(report.evilTwins) { twin ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier = Modifier.padding(14.dp)) {
                            Text(twin.ssid, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text("${twin.bssid} · ${twin.security} · ${twin.signalDbm} dBm", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            item {
                Text("Находки", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            }
            items(report.findings) { f ->
                FindingCard(f)
            }

            diff?.let { d ->
                if (d.appeared.isNotEmpty() || d.disappeared.isNotEmpty()) {
                    item {
                        Text("Изменения с прошлого скана", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    }
                    items(d.appeared) { dev ->
                        Text("+ ${dev.displayName} (${dev.ip})", color = Success, style = MaterialTheme.typography.bodyMedium)
                    }
                    items(d.disappeared) { dev ->
                        Text("- ${dev.displayName} (${dev.ip})", color = Danger, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingCard(f: SecurityFinding) {
    val color = when (f.severity) {
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
                Text(f.title, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(f.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
