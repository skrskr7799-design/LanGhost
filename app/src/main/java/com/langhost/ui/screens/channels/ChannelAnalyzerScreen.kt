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
import com.langhost.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChannelAnalyzerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { NetworkRepository.getInstance(context) }
    val network by repo.networkInfo.collectAsState()

    val networks by repo.nearbyNetworks.collectAsState()
    val nearbyScanCompleted by repo.nearbyScanCompleted.collectAsState()
    val nearbyScanTime by repo.nearbyScanTime.collectAsState()
    var scanning by remember { mutableStateOf(false) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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
                        Text("Анализ радиоэфира", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(advice.recommendation, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(
                            "Оценка учитывает обнаруженные точки доступа и уровень их сигнала. Поддержка каналов зависит от региона и роутера.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                        when {
                            !nearbyScanCompleted -> {
                                Text("Свежих данных пока нет. До сканирования пустые каналы не считаются свободными.", style = MaterialTheme.typography.bodySmall, color = Warning)
                                TextButton(onClick = { scan() }) { Text("Сканировать эфир") }
                            }
                            networks.isEmpty() -> {
                                Text("Сканирование завершено: соседние точки доступа не обнаружены.", style = MaterialTheme.typography.bodySmall, color = Success)
                                TextButton(onClick = { scan() }) { Text("Обновить сканирование") }
                            }
                            else -> {
                                Text("${networks.size} обнаруженных сетей", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                if (nearbyScanTime > 0L) {
                                    Text(
                                        "Последнее сканирование: " +
                                            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                                .format(java.util.Date(nearbyScanTime)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextTertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (nearbyScanCompleted && advice.band24.isNotEmpty()) {
                item { Text("2.4 ГГц", style = MaterialTheme.typography.titleMedium, color = TextSecondary) }
                items(advice.band24.filter { it.networkCount > 0 || it.channel in listOf(1, 6, 11) }) { load ->
                    ChannelBar(load, advice.best24 == load.channel)
                }
            }

            val visible5 = advice.band5.filter { it.networkCount > 0 || it.channel == advice.best5 }
            if (visible5.isNotEmpty()) {
                item { Text("5 ГГц · обнаруженные и рекомендуемый", style = MaterialTheme.typography.titleMedium, color = TextSecondary) }
                items(visible5) { load ->
                    ChannelBar(load, advice.best5 == load.channel)
                }
            }

            val visible6 = advice.band6.filter { it.networkCount > 0 || it.channel == advice.best6 }
            if (visible6.isNotEmpty()) {
                item { Text("6 ГГц · обнаруженные и рекомендуемый", style = MaterialTheme.typography.titleMedium, color = TextSecondary) }
                items(visible6) { load ->
                    ChannelBar(load, advice.best6 == load.channel)
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

