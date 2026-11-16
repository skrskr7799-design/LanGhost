package com.langhost.ui.screens.wifi

import android.Manifest
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
import com.langhost.data.NetworkRepository
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.PrivacyFormatter
import com.langhost.data.model.WifiNetwork
import com.langhost.ui.theme.*
import com.langhost.ui.theme.TechnicalTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class WifiBandFilter(val label: String) {
    ALL("Все"), GHZ_24("2.4 ГГц"), GHZ_5("5 ГГц"), GHZ_6("6 ГГц")
}

private enum class WifiSort(val label: String) {
    SIGNAL("По сигналу"), NAME("По имени")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NearbyNetworksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { NetworkRepository.getInstance(context) }
    val networks by repo.nearbyNetworks.collectAsState()
    val scanCompleted by repo.nearbyScanCompleted.collectAsState()
    val scanTime by repo.nearbyScanTime.collectAsState()
    var isScanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedBand by remember { mutableStateOf(WifiBandFilter.ALL) }
    var sort by remember { mutableStateOf(WifiSort.SIGNAL) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    fun doScan() {
        scanError = null
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
            return
        }
        scope.launch {
            isScanning = true
            try {
                repo.scanNearbyNetworks()
                if (repo.nearbyScanTime.value == 0L) {
                    scanError = "Android не обновил результаты. Показаны доступные ранее данные, если они есть."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                scanError = "Нет разрешения на получение результатов Wi-Fi."
            } catch (_: Exception) {
                scanError = "Не удалось выполнить сканирование. Попробуйте ещё раз."
            } finally {
                isScanning = false
            }
        }
    }

    val filteredNetworks = remember(networks, query, selectedBand, sort) {
        val filtered = networks.filter { network ->
            val matchesBand = when (selectedBand) {
                WifiBandFilter.ALL -> true
                WifiBandFilter.GHZ_24 -> network.frequencyBand == "2.4 GHz"
                WifiBandFilter.GHZ_5 -> network.frequencyBand == "5 GHz"
                WifiBandFilter.GHZ_6 -> network.frequencyBand == "6 GHz"
            }
            val needle = query.trim()
            val matchesQuery = needle.isBlank() ||
                network.ssid.contains(needle, ignoreCase = true) ||
                network.bssid.contains(needle, ignoreCase = true) ||
                network.channel.toString().contains(needle)
            matchesBand && matchesQuery
        }
        when (sort) {
            WifiSort.SIGNAL -> filtered.sortedByDescending { it.signalDbm }
            WifiSort.NAME -> filtered.sortedBy { it.ssid.ifBlank { "~~~~" }.lowercase(Locale.getDefault()) }
        }
    }

    val count24 = networks.count { it.frequencyBand == "2.4 GHz" }
    val count5 = networks.count { it.frequencyBand == "5 GHz" }
    val count6 = networks.count { it.frequencyBand == "6 GHz" }
    val lastScanLabel = remember(scanTime) {
        if (scanTime <= 0L) "Ещё не обновлялось"
        else "Обновлено в ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(scanTime))}"
    }
    val resultsFresh = scanTime > 0L && System.currentTimeMillis() >= scanTime &&
        System.currentTimeMillis() - scanTime <= 5 * 60 * 1000L

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Соседние сети", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (networks.isNotEmpty()) "${networks.size} обнаружено · $lastScanLabel" else "Wi-Fi окружение",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { doScan() }, enabled = !isScanning) {
                        if (isScanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        else Icon(Icons.Default.Radar, contentDescription = "Сканировать", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 1120.dp).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Радиоэфир", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when {
                                        isScanning -> "Идёт поиск точек доступа"
                                        !permissionState.allPermissionsGranted -> "Требуется разрешение Android"
                                        networks.isEmpty() && scanCompleted -> "Сканирование завершено без результатов"
                                        networks.isEmpty() -> "Ожидает первого сканирования"
                                        resultsFresh -> "Данные актуальны · до 5 минут"
                                        else -> "Данные могут быть устаревшими"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (resultsFresh) Success else TextSecondary
                                )
                            }
                            Icon(Icons.Default.Wifi, null, tint = Primary, modifier = Modifier.size(28.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WifiMetric("Всего", networks.size.toString(), Modifier.weight(1f))
                            WifiMetric("2.4 ГГц", count24.toString(), Modifier.weight(1f))
                            WifiMetric("5 ГГц", count5.toString(), Modifier.weight(1f))
                            WifiMetric("6 ГГц", count6.toString(), Modifier.weight(1f))
                        }
                        if (isScanning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary, trackColor = SurfaceVariant)
                        if (!permissionState.allPermissionsGranted) {
                            Text(
                                "Для поиска соседних точек доступа Android требует разрешения на доступ к данным Wi-Fi и местоположению.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Button(onClick = { permissionState.launchMultiplePermissionRequest() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.LockOpen, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Предоставить разрешения")
                            }
                        } else if (networks.isEmpty() && !isScanning) {
                            Button(onClick = { doScan() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Radar, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Начать сканирование")
                            }
                        }
                        if (scanError != null) {
                            Text(scanError!!, style = MaterialTheme.typography.bodySmall, color = Warning)
                        }
                    }
                }
            }

            if (networks.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Имя сети, BSSID или канал") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить")
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Border,
                            focusedLeadingIconColor = Primary,
                            unfocusedLeadingIconColor = TextTertiary
                        )
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WifiBandFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedBand == filter,
                                onClick = { selectedBand = filter },
                                label = { Text(filter.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.18f),
                                    selectedLabelColor = Primary,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${filteredNetworks.size} из ${networks.size} сетей", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Box {
                            TextButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Default.Sort, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(sort.label)
                            }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                WifiSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = { sort = option; sortMenuExpanded = false },
                                        leadingIcon = { if (sort == option) Icon(Icons.Default.Check, null) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (filteredNetworks.isEmpty()) {
                    item {
                        WifiEmptyState(
                            title = "Ничего не найдено",
                            message = "Измени запрос или выбери другой диапазон.",
                            icon = Icons.Default.SearchOff
                        )
                    }
                } else {
                    items(filteredNetworks, key = { it.bssid }) { network ->
                        WifiNetworkCard(network)
                    }
                }
            } else if (!isScanning && permissionState.allPermissionsGranted) {
                item {
                    WifiEmptyState(
                        title = if (scanCompleted) "Точки доступа не обнаружены" else "Сети ещё не отсканированы",
                        message = if (scanCompleted) "Android вернул пустой список. Это не обязательно означает, что рядом нет Wi-Fi." else "Запусти сканирование, чтобы собрать данные о ближайших точках доступа.",
                        icon = Icons.Default.WifiOff
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun WifiMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary, maxLines = 1)
    }
}

@Composable
private fun WifiEmptyState(title: String, message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun WifiNetworkCard(network: WifiNetwork) {
    val signalTone = when {
        network.signalDbm >= -60 -> Success
        network.signalDbm >= -70 -> Warning
        else -> Danger
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (network.isCurrent) Primary.copy(alpha = 0.12f) else Surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (network.isCurrent) Primary.copy(alpha = 0.55f) else Border.copy(alpha = 0.72f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        network.ssid.ifBlank { "Скрытая сеть" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (network.isCurrent) WifiTag("Текущая", Primary)
                        WifiTag(network.frequencyBand, TextSecondary)
                        WifiTag(network.security, TextSecondary)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${network.signalDbm} dBm", style = MaterialTheme.typography.titleMedium, color = signalTone, fontWeight = FontWeight.SemiBold)
                    Text(signalDescription(network.signalDbm), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WifiDetail("Канал", network.channel.toString(), Modifier.weight(0.7f))
                WifiDetail("Частота", "${network.frequencyMhz} MHz", Modifier.weight(1f))
                WifiDetail("BSSID", PrivacyFormatter.bssid(network.bssid, appSettings.maskNetworkIdentifiers), Modifier.weight(1.5f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { index ->
                    Box(
                        Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (index < signalBars(network.signalDbm)) signalTone else SurfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiTag(text: String, tint: androidx.compose.ui.graphics.Color) {
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun WifiDetail(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(value, style = TechnicalTypography, color = TextSecondary, maxLines = 1)
    }
}

private fun signalDescription(dbm: Int): String = when {
    dbm >= -50 -> "Отличный"
    dbm >= -60 -> "Хороший"
    dbm >= -70 -> "Средний"
    dbm >= -80 -> "Слабый"
    else -> "Очень слабый"
}

private fun signalBars(dbm: Int): Int = when {
    dbm >= -50 -> 5
    dbm >= -60 -> 4
    dbm >= -70 -> 3
    dbm >= -80 -> 2
    else -> 1
}
