package com.langhost.ui.screens.devices

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.langhost.data.ExportHelper
import com.langhost.data.model.Device
import com.langhost.ui.screens.dashboard.DashboardViewModel
import com.langhost.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    onDeviceClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val devices = state.devices
    val context = LocalContext.current
    var showExportMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Устройства", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (devices.isNotEmpty()) {
                            Text("${devices.size} хостов", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
                    }
                },
                actions = {
                    if (devices.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Default.Share, contentDescription = "Экспорт", tint = TextPrimary)
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Экспорт в TXT") },
                                    onClick = {
                                        showExportMenu = false
                                        val intent = ExportHelper.exportDevicesAsText(context, devices)
                                        if (intent != null) context.startActivity(Intent.createChooser(intent, "Экспорт"))
                                        else Toast.makeText(context, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Экспорт в CSV") },
                                    onClick = {
                                        showExportMenu = false
                                        val intent = ExportHelper.exportDevicesAsCsv(context, devices)
                                        if (intent != null) context.startActivity(Intent.createChooser(intent, "Экспорт"))
                                        else Toast.makeText(context, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.startScan() }, enabled = !state.isScanning) {
                        if (state.isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        } else {
                            Icon(Icons.Default.Radar, contentDescription = "Сканировать", tint = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Devices, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                    Text("Устройства ещё не найдены", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    Text("Нажмите на радар, чтобы начать сканирование", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(devices, key = { it.ip }) { device ->
                    DeviceCard(device = device, onClick = { onDeviceClick(device.ip) })
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: Device, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(if (device.isOnline) Success else TextTertiary)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (device.isFavorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = Secondary, modifier = Modifier.size(16.dp))
                    }
                    if (device.isGateway) {
                        Spacer(Modifier.width(6.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Шлюз", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Primary.copy(alpha = 0.15f),
                                labelColor = Primary
                            ),
                            modifier = Modifier.height(22.dp)
                        )
                    }
                    if (device.isLocalDevice) {
                        Spacer(Modifier.width(6.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Это устройство", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Secondary.copy(alpha = 0.15f),
                                labelColor = Secondary
                            ),
                            modifier = Modifier.height(22.dp)
                        )
                    }
                }

                Text(
                    text = buildString {
                        append(device.ip)
                        if (device.mac.isNotBlank()) append("  •  ${device.mac}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (device.vendor.isNotBlank()) {
                    Text(text = device.vendor, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary)
        }
    }
}
