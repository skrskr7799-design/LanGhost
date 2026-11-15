package com.langhost.ui.screens.wifi

import android.Manifest
import android.content.Context
import android.os.Build
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.langhost.data.NetworkRepository
import com.langhost.data.model.WifiNetwork
import com.langhost.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NearbyNetworksScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { NetworkRepository.getInstance(context) }
    val networks by repo.nearbyNetworks.collectAsState()
    val scanCompleted by repo.nearbyScanCompleted.collectAsState()
    var isScanning by remember { mutableStateOf(false) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    fun doScan() {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
            return
        }
        scope.launch {
            isScanning = true
            try {
                repo.scanNearbyNetworks()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            } finally {
                isScanning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Соседние сети", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (networks.isNotEmpty()) {
                            Text("${networks.size} сетей", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { doScan() }, enabled = !isScanning) {
                        if (isScanning) {
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
        if (networks.isEmpty() && !isScanning) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                    Text(
                        if (scanCompleted) "Нет доступных результатов сканирования" else "Сети ещё не отсканированы",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Text("Нажмите на радар для поиска соседних Wi-Fi", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { doScan() }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        Text("Сканировать")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(networks, key = { it.bssid }) { network ->
                    WifiNetworkCard(network)
                }
            }
        }
    }
}

@Composable
private fun WifiNetworkCard(network: WifiNetwork) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (network.isCurrent) Primary.copy(alpha = 0.12f) else Surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = when (network.signalLevel) {
                            4, 3 -> Success
                            2 -> Secondary
                            else -> TextTertiary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = network.ssid.ifBlank { "Скрытая сеть" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    if (network.isCurrent) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Текущая", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Primary.copy(alpha = 0.2f),
                                labelColor = Primary
                            ),
                            modifier = Modifier.height(22.dp)
                        )
                    }
                }
                Text(
                    text = "${network.signalDbm} dBm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Text(
                text = "${network.frequencyBand}  •  Канал ${network.channel}  •  ${network.security}",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
            Text(
                text = network.bssid,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )

            LinearProgressIndicator(
                progress = { (network.signalLevel / 4f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = when (network.signalLevel) {
                    4, 3 -> Success
                    2 -> Secondary
                    else -> Danger
                },
                trackColor = SurfaceVariant
            )
        }
    }
}

