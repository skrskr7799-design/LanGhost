package com.langhost.ui.screens.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.background
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
import com.langhost.data.model.WifiNetwork
import com.langhost.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NearbyNetworksScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var networks by remember { mutableStateOf<List<WifiNetwork>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var lastScanTime by remember { mutableStateOf(0L) }

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
            networks = scanNearbyNetworks(context)
            lastScanTime = System.currentTimeMillis()
            isScanning = false
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
                    Text("Сети ещё не отсканированы", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
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

            // Signal bar
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

@SuppressLint("MissingPermission")
private suspend fun scanNearbyNetworks(context: Context): List<WifiNetwork> {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // Trigger scan
    @Suppress("DEPRECATION")
    wifiManager.startScan()

    // Wait a bit for results
    delay(2500)

    val currentBssid = wifiManager.connectionInfo?.bssid

    @Suppress("DEPRECATION")
    val results = wifiManager.scanResults ?: emptyList()

    return results
        .map { result ->
            val freq = result.frequency
            val channel = when {
                freq in 2412..2484 -> (freq - 2412) / 5 + 1
                freq in 5170..5825 -> (freq - 5170) / 5 + 34
                else -> 0
            }
            val security = when {
                result.capabilities.contains("WPA3") -> "WPA3"
                result.capabilities.contains("WPA2") -> "WPA2"
                result.capabilities.contains("WPA") -> "WPA"
                result.capabilities.contains("WEP") -> "WEP"
                else -> "Open"
            }
            WifiNetwork(
                ssid = result.SSID ?: "",
                bssid = result.BSSID ?: "",
                signalDbm = result.level,
                frequencyMhz = freq,
                channel = channel,
                security = security,
                isCurrent = result.BSSID == currentBssid
            )
        }
        .distinctBy { it.bssid }
        .sortedByDescending { it.signalDbm }
}
