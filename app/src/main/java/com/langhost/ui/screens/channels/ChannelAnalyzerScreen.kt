package com.langhost.ui.screens.channels

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Radar
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
import com.langhost.data.ChannelAnalyzer
import com.langhost.data.ChannelLoad
import com.langhost.data.NetworkRepository
import com.langhost.data.model.WifiNetwork
import com.langhost.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChannelAnalyzerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { NetworkRepository.getInstance(context) }
    val network by repo.networkInfo.collectAsState()

    val networks by repo.nearbyNetworks.collectAsState()
    var scanning by remember { mutableStateOf(false) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    val advice = remember(networks, network.channel) {
        ChannelAnalyzer.analyze(networks, network.channel)
    }

    fun scan() {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
            return
        }
        scope.launch {
            scanning = true
            try {
                repo.scanNearbyNetworks()
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            } finally {
                scanning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Каналы Wi-Fi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { scan() }, enabled = !scanning) {
                        if (scanning) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        else Icon(Icons.Default.Radar, "Сканировать", tint = TextPrimary)
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
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Рекомендация", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(advice.recommendation, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        if (networks.isEmpty()) {
                            TextButton(onClick = { scan() }) { Text("Сканировать эфир") }
                        }
                    }
                }
            }

            if (advice.band24.isNotEmpty()) {
                item { Text("2.4 ГГц", style = MaterialTheme.typography.titleMedium, color = TextSecondary) }
                items(advice.band24.filter { it.networkCount > 0 || it.channel in listOf(1, 6, 11) }) { load ->
                    ChannelBar(load, advice.best24 == load.channel)
                }
            }

            if (advice.band5.isNotEmpty()) {
                item { Text("5 ГГц", style = MaterialTheme.typography.titleMedium, color = TextSecondary) }
                items(advice.band5) { load ->
                    ChannelBar(load, advice.best5 == load.channel)
                }
            }
        }
    }
}

@Composable
private fun ChannelBar(load: ChannelLoad, isBest: Boolean) {
    val maxCount = 8f
    val frac = (load.networkCount / maxCount).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isBest) Primary.copy(alpha = 0.12f) else Surface
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Канал ${load.channel}${if (isBest) " · лучший" else ""}",
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text("${load.networkCount} сетей", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = when {
                    load.networkCount == 0 -> Success
                    load.networkCount <= 2 -> Secondary
                    else -> Danger
                },
                trackColor = SurfaceVariant
            )
        }
    }
}

