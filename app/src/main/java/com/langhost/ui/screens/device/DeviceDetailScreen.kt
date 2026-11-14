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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.langhost.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceIp: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: DeviceDetailViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DeviceDetailViewModel(
                    application = context.applicationContext as android.app.Application,
                    deviceIp = deviceIp
                ) as T
            }
        }
    )

    val state by viewModel.uiState.collectAsState()
    val device = state.device

    Scaffold(
        modifier = modifier,
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
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (state.isFavorite) Secondary else TextPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.toggleWatched() }) {
                        Icon(
                            imageVector = if (state.isWatched) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Watch",
                            tint = if (state.isWatched) Primary else TextPrimary
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
            // Main info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Device Info", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    HorizontalDivider(color = Divider)
                    InfoRow("IP Address", device.ip)
                    InfoRow("MAC Address", device.mac.ifBlank { "—" })
                    InfoRow("Hostname", device.hostname.ifBlank { "—" })
                    InfoRow("Vendor", device.vendor.ifBlank { "Unknown" })
                    InfoRow("Status", if (device.isOnline) "Online" else "Offline")
                    when {
                        device.isGateway -> InfoRow("Role", "Gateway / Router")
                        device.isLocalDevice -> InfoRow("Role", "This device")
                    }
                }
            }

            // Port Scanner
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
                        Text("Port Scanner", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Button(
                            onClick = { viewModel.scanPorts() },
                            enabled = !state.isScanningPorts,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            if (state.isScanningPorts) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = OnPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Scanning...")
                            } else {
                                Text("Scan Ports")
                            }
                        }
                    }

                    if (state.openPorts.isNotEmpty()) {
                        Text(
                            text = "Open ports (${state.openPorts.size}):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = state.openPorts.joinToString(", "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Success,
                            fontWeight = FontWeight.Medium
                        )

                        // Simple service hints
                        val serviceHints = state.openPorts.mapNotNull { port ->
                            when (port) {
                                21 -> "FTP"
                                22 -> "SSH"
                                23 -> "Telnet"
                                25 -> "SMTP"
                                53 -> "DNS"
                                80 -> "HTTP"
                                110 -> "POP3"
                                139, 445 -> "SMB"
                                143 -> "IMAP"
                                443 -> "HTTPS"
                                993 -> "IMAPS"
                                995 -> "POP3S"
                                1723 -> "PPTP"
                                3306 -> "MySQL"
                                3389 -> "RDP"
                                5900 -> "VNC"
                                8080, 8443 -> "HTTP-Alt"
                                9100 -> "Printer"
                                else -> null
                            }?.let { "$port ($it)" }
                        }
                        if (serviceHints.isNotEmpty()) {
                            Text(
                                text = serviceHints.joinToString("  •  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    } else if (!state.isScanningPorts) {
                        Text(
                            text = "No open ports found or scan not performed",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }

            // Nickname & Notes
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Customization", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

                    OutlinedTextField(
                        value = state.nickname,
                        onValueChange = { viewModel.updateNickname(it) },
                        label = { Text("Nickname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Border,
                            focusedLabelColor = Primary,
                            cursorColor = Primary
                        )
                    )

                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = { viewModel.updateNotes(it) },
                        label = { Text("Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Border,
                            focusedLabelColor = Primary,
                            cursorColor = Primary
                        )
                    )

                    Button(
                        onClick = { viewModel.saveCustomization() },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text(if (state.isSaving) "Saving..." else "Save")
                    }
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
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
