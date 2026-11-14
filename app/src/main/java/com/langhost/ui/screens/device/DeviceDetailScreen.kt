package com.langhost.ui.screens.device

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.langhost.data.model.Device
import com.langhost.ui.screens.dashboard.DashboardViewModel
import com.langhost.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceIp: String,
    onBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val device = state.devices.find { it.ip == deviceIp }
        ?: viewModel.getDevices().find { it.ip == deviceIp }

    var isScanningPorts by remember { mutableStateOf(false) }
    var openPorts by remember { mutableStateOf<List<Int>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = device?.displayName ?: deviceIp,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
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
        if (device == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Device not found", color = TextSecondary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoRow("IP Address", device.ip)
                    InfoRow("MAC Address", device.mac.ifBlank { "—" })
                    InfoRow("Hostname", device.hostname.ifBlank { "—" })
                    InfoRow("Vendor", device.vendor.ifBlank { "Unknown" })
                    InfoRow("Status", if (device.isOnline) "Online" else "Offline")
                    if (device.isGateway) InfoRow("Role", "Gateway / Router")
                    if (device.isLocalDevice) InfoRow("Role", "This device")
                }
            }

            // Port scanner section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Port Scanner",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Button(
                            onClick = {
                                isScanningPorts = true
                                scope.launch {
                                    // We need access to repository — temporary workaround via viewModel later
                                    // For now just simulate / placeholder
                                    openPorts = listOf(80, 443) // placeholder until full wiring
                                    isScanningPorts = false
                                }
                            },
                            enabled = !isScanningPorts,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            if (isScanningPorts) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = OnPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (isScanningPorts) "Scanning..." else "Scan Ports")
                        }
                    }

                    if (openPorts.isNotEmpty()) {
                        Text(
                            text = "Open ports: ${openPorts.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Success
                        )
                    } else if (!isScanningPorts) {
                        Text(
                            text = "No scan performed yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }

            // Notes / Nickname section (UI only for now)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Notes & Tags",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Nickname and notes will be available in the next update",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
