package com.langhost.ui.screens.wifi

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.langhost.ui.components.DataNoticeTone
import com.langhost.ui.components.DataQualityNotice
import com.langhost.ui.components.ErrorState
import com.langhost.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class WifiBandFilter(val label: String) {
    ALL("Все"), GHZ_24("2.4 ГГц"), GHZ_5("5 ГГц"), GHZ_6("6 ГГц")
}
private enum class WifiSort(val label: String) { SIGNAL("По сигналу"), NAME("По имени") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NearbyNetworksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { NetworkRepository.getInstance(context) }
    val appSettings by remember(context) { AppSettings(context.applicationContext).settingsFlow }.collectAsState(initial = AppSettingsData())
    val networks by repo.nearbyNetworks.collectAsState()
    val scanCompleted by repo.nearbyScanCompleted.collectAsState()
    val scanTime by repo.nearbyScanTime.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var band by remember { mutableStateOf(WifiBandFilter.ALL) }
    var sort by remember { mutableStateOf(WifiSort.SIGNAL) }
    var sortExpanded by remember { mutableStateOf(false) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    fun scan() {
        scanError = null
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
            return
        }
        scope.launch {
            scanning = true
            try {
                repo.scanNearbyNetworks()
                if (repo.nearbyScanTime.value == 0L) scanError = "Android не обновил результаты. Данные могут быть устаревшими."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                scanError = "Нет разрешения на получение результатов Wi-Fi."
            } catch (_: Exception) {
                scanError = "Не удалось выполнить сканирование. Попробуйте ещё раз."
            } finally {
                scanning = false
            }
        }
    }

    val filtered = remember(networks, query, band, sort) {
        val subset = networks.filter { n ->
            val bandMatch = when (band) {
                WifiBandFilter.ALL -> true
                WifiBandFilter.GHZ_24 -> n.frequencyBand == "2.4 GHz"
                WifiBandFilter.GHZ_5 -> n.frequencyBand == "5 GHz"
                WifiBandFilter.GHZ_6 -> n.frequencyBand == "6 GHz"
            }
            val q = query.trim()
            bandMatch && (q.isBlank() || n.ssid.contains(q, true) || n.bssid.contains(q, true) || n.channel.toString().contains(q))
        }
        when (sort) {
            WifiSort.SIGNAL -> subset.sortedByDescending { it.signalDbm }
            WifiSort.NAME -> subset.sortedBy { it.ssid.ifBlank { "~~~~" }.lowercase(Locale.getDefault()) }
        }
    }
    val count24 = networks.count { it.frequencyBand == "2.4 GHz" }
    val count5 = networks.count { it.frequencyBand == "5 GHz" }
    val count6 = networks.count { it.frequencyBand == "6 GHz" }
    val strongest = networks.maxByOrNull { it.signalDbm }
    val lastScan = if (scanTime > 0L) SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(scanTime)) else "Нет данных"
    val fresh = scanTime > 0L && System.currentTimeMillis() >= scanTime && System.currentTimeMillis() - scanTime <= 5 * 60 * 1000L

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Wi-Fi окружение", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${networks.size} точек доступа · ${if (scanTime > 0L) "обновлено $lastScan" else "ожидает сканирования"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextPrimary) } },
                actions = {
                    IconButton(onClick = { scan() }, enabled = !scanning) {
                        if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        else Icon(Icons.Default.Radar, "Сканировать", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(Modifier.weight(1.25f).fillMaxHeight()) {
                        NetworkInventory(
                            networks, filtered, appSettings.maskNetworkIdentifiers, query, { query = it },
                            band, { band = it }, sort, { sort = it }, sortExpanded, { sortExpanded = it },
                            scanning, scanError, permissionState.allPermissionsGranted,
                            { permissionState.launchMultiplePermissionRequest() }, ::scan,
                            Modifier.weight(1f)
                        )
                    }
                    Column(
                        Modifier.weight(0.75f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        WifiEnvironmentPanel(networks.size, count24, count5, count6, fresh, scanCompleted, scanning, lastScan, scanError, ::scan)
                        WifiBandPanel("2.4 ГГц", networks.filter { it.frequencyBand == "2.4 GHz" })
                        WifiBandPanel("5 ГГц", networks.filter { it.frequencyBand == "5 GHz" })
                        WifiBandPanel("6 ГГц", networks.filter { it.frequencyBand == "6 GHz" })
                        if (strongest != null) StrongestNetworkPanel(strongest, appSettings.maskNetworkIdentifiers)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { WifiEnvironmentPanel(networks.size, count24, count5, count6, fresh, scanCompleted, scanning, lastScan, scanError, ::scan) }
                    item {
                        if (!permissionState.allPermissionsGranted) {
                            Button(onClick = { permissionState.launchMultiplePermissionRequest() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.LockOpen, null); Spacer(Modifier.width(8.dp)); Text("Предоставить разрешения")
                            }
                        }
                    }
                    item {
                        if (networks.isNotEmpty()) {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                                singleLine = true, placeholder = { Text("Имя сети, BSSID или канал") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Очистить") } },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = Primary, unfocusedBorderColor = Border)
                            )
                        }
                    }
                    item { BandFilters(band, { band = it }) }
                    item { SortControl(filtered.size, networks.size, sort, { sortExpanded = true }, sortExpanded, { sortExpanded = false }, { sort = it; sortExpanded = false }) }
                    if (filtered.isEmpty()) {
                        item { WifiEmptyState(if (networks.isEmpty()) "Сети ещё не обнаружены" else "Ничего не найдено", if (networks.isEmpty()) "Запусти сканирование, чтобы собрать сведения о точках доступа." else "Измени запрос или выбери другой диапазон.", Icons.Default.WifiOff) }
                    } else {
                        items(filtered, key = { it.bssid }) { WifiNetworkCard(it, appSettings.maskNetworkIdentifiers) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkInventory(
    networks: List<WifiNetwork>, filtered: List<WifiNetwork>, mask: Boolean,
    query: String, onQuery: (String) -> Unit, band: WifiBandFilter, onBand: (WifiBandFilter) -> Unit,
    sort: WifiSort, onSort: (WifiSort) -> Unit, sortExpanded: Boolean, onSortExpanded: (Boolean) -> Unit,
    scanning: Boolean, error: String?, hasPermission: Boolean, onPermission: () -> Unit, onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Обнаруженные точки", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("${filtered.size} из ${networks.size} · сортировка: ${sort.label.lowercase()}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Button(onClick = onScan, enabled = !scanning && hasPermission, shape = RoundedCornerShape(12.dp)) {
                if (scanning) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Radar, null)
                Spacer(Modifier.width(7.dp)); Text("Сканировать")
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Имя сети, BSSID или канал") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = Primary, unfocusedBorderColor = Border)
        )
        Spacer(Modifier.height(8.dp))
        BandFilters(band, onBand)
        Spacer(Modifier.height(6.dp))
        SortControl(filtered.size, networks.size, sort, { onSortExpanded(true) }, sortExpanded, { onSortExpanded(false) }, onSort)
        if (!hasPermission) {
            Text("Для сканирования требуется разрешение Android.", color = Warning, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onPermission) { Text("Предоставить разрешения") }
        }
        if (error != null) {
            ErrorState(
                title = "Сканирование не завершено",
                description = error,
                onRetry = onScan
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (filtered.isEmpty()) {
                item { WifiEmptyState("Нет результатов", "Проверь фильтры или обнови сканирование.", Icons.Default.SearchOff) }
            } else {
                items(filtered, key = { it.bssid }) { WifiNetworkCard(it, mask) }
            }
        }
    }
}

@Composable
private fun WifiEnvironmentPanel(
    total: Int, count24: Int, count5: Int, count6: Int, fresh: Boolean, completed: Boolean,
    scanning: Boolean, lastScan: String, error: String?, onScan: () -> Unit
) {
    Surface(color = Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Border.copy(alpha = 0.8f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("РАДИОЭФИР / LIVE SNAPSHOT", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                    Text("Окружение сети", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        when { scanning -> "Выполняется сканирование"; fresh -> "Свежие данные · до 5 минут"; completed -> "Последнее обновление: $lastScan"; else -> "Нет подтверждённого сканирования" },
                        style = MaterialTheme.typography.bodySmall, color = if (fresh) Success else TextSecondary
                    )
                }
                Icon(Icons.Default.Wifi, null, tint = AccentCyan, modifier = Modifier.size(30.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WifiMetric("Всего", total.toString(), Modifier.weight(1f))
                WifiMetric("2.4 ГГц", count24.toString(), Modifier.weight(1f))
                WifiMetric("5 ГГц", count5.toString(), Modifier.weight(1f))
                WifiMetric("6 ГГц", count6.toString(), Modifier.weight(1f))
            }
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
            if (completed && !fresh && total > 0 && !scanning) {
                DataQualityNotice(
                    title = "Данные могут быть устаревшими",
                    description = "Последний снимок радиоэфира не обновлялся более пяти минут.",
                    tone = DataNoticeTone.Warning
                )
            }
            if (total == 0 && !scanning) {
                DataQualityNotice(
                    title = if (completed) "Нет подтверждённых результатов" else "Сканирование ещё не выполнялось",
                    description = "Пустой результат не доказывает отсутствие сетей поблизости. Запустите сканирование, чтобы получить актуальные данные.",
                    tone = DataNoticeTone.Info
                )
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Radar, null); Spacer(Modifier.width(8.dp)); Text("Начать сканирование") }
            }
            if (error != null) {
                ErrorState(
                    title = "Сканирование не завершено",
                    description = error,
                    onRetry = onScan
                )
            }
        }
    }
}

@Composable
private fun WifiBandPanel(title: String, networks: List<WifiNetwork>) {
    val sorted = networks.sortedByDescending { it.signalDbm }
    Surface(color = Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border.copy(alpha = 0.65f))) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("${networks.size} сетей", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            if (sorted.isEmpty()) Text("Нет обнаруженных точек", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            else {
                sorted.take(3).forEach { network ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(network.ssid.ifBlank { "Скрытая сеть" }, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("CH ${network.channel}", style = TechnicalTypography, color = TextTertiary)
                        Text("${network.signalDbm} dBm", modifier = Modifier.padding(start = 10.dp), style = TechnicalTypography, color = signalColor(network.signalDbm))
                    }
                }
            }
        }
    }
}

@Composable
private fun StrongestNetworkPanel(network: WifiNetwork, mask: Boolean) {
    Surface(color = SurfaceHighlight.copy(alpha = 0.42f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border.copy(alpha = 0.7f))) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("СИЛЬНЕЙШИЙ СИГНАЛ", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.SemiBold)
            Text(network.ssid.ifBlank { "Скрытая сеть" }, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("${network.signalDbm} dBm · ${network.frequencyBand} · канал ${network.channel}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("BSSID · ${PrivacyFormatter.bssid(network.bssid, mask)}", style = TechnicalTypography, color = TextTertiary)
        }
    }
}

@Composable
private fun BandFilters(selected: WifiBandFilter, onSelect: (WifiBandFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WifiBandFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter, onClick = { onSelect(filter) }, label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.18f), selectedLabelColor = Primary, labelColor = TextSecondary)
            )
        }
    }
}

@Composable
private fun SortControl(
    visible: Int, total: Int, sort: WifiSort, onOpen: () -> Unit,
    expanded: Boolean, onDismiss: () -> Unit, onSelect: (WifiSort) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("$visible из $total точек", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Box {
            TextButton(onClick = onOpen) { Icon(Icons.Default.Sort, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(sort.label) }
            DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
                WifiSort.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option) }, leadingIcon = { if (sort == option) Icon(Icons.Default.Check, null) })
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
    Surface(modifier = Modifier.fillMaxWidth(), color = Surface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun WifiNetworkCard(network: WifiNetwork, maskIdentifiers: Boolean) {
    val tone = signalColor(network.signalDbm)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (network.isCurrent) Primary.copy(alpha = 0.11f) else Surface,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, if (network.isCurrent) Primary.copy(alpha = 0.6f) else Border.copy(alpha = 0.7f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(network.ssid.ifBlank { "Скрытая сеть" }, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (network.isCurrent) WifiTag("Текущая", Primary)
                        WifiTag(network.frequencyBand, TextSecondary)
                        WifiTag(network.security, TextSecondary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${network.signalDbm} dBm", style = MaterialTheme.typography.titleMedium, color = tone, fontWeight = FontWeight.SemiBold)
                    Text(signalDescription(network.signalDbm), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WifiDetail("Канал", network.channel.toString(), Modifier.weight(0.65f))
                WifiDetail("Частота", "${network.frequencyMhz} MHz", Modifier.weight(1f))
                WifiDetail("BSSID", PrivacyFormatter.bssid(network.bssid, maskIdentifiers), Modifier.weight(1.4f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(5) { index ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(3.dp)).background(if (index < signalBars(network.signalDbm)) tone else SurfaceVariant))
                }
            }
        }
    }
}

@Composable
private fun WifiTag(text: String, tint: androidx.compose.ui.graphics.Color) {
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun WifiDetail(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(value, style = TechnicalTypography, color = TextSecondary, maxLines = 1)
    }
}

private fun signalColor(dbm: Int) = when { dbm >= -60 -> Success; dbm >= -70 -> Warning; else -> Danger }
private fun signalDescription(dbm: Int) = when { dbm >= -50 -> "Отличный"; dbm >= -60 -> "Хороший"; dbm >= -70 -> "Средний"; dbm >= -80 -> "Слабый"; else -> "Очень слабый" }
private fun signalBars(dbm: Int) = when { dbm >= -50 -> 5; dbm >= -60 -> 4; dbm >= -70 -> 3; dbm >= -80 -> 2; else -> 1 }
