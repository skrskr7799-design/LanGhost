package com.langhost.ui.screens.devices

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.PrivacyFormatter
import com.langhost.data.model.Device
import com.langhost.data.model.DeviceType
import com.langhost.data.model.DiscoveryEvidence
import com.langhost.ui.components.ContentPanel
import com.langhost.ui.components.DataNoticeTone
import com.langhost.ui.components.DataQualityNotice
import com.langhost.ui.components.EmptyState
import com.langhost.ui.components.PageHeading
import com.langhost.ui.components.SectionHeading
import com.langhost.ui.components.StatusBadge
import com.langhost.ui.components.StatusTone
import com.langhost.ui.screens.dashboard.DashboardViewModel
import com.langhost.ui.theme.*
import com.langhost.ui.theme.TechnicalTypography

private enum class DeviceFilter(val label: String) {
    ALL("Все"), ONLINE("Онлайн"), OFFLINE("Не подтверждено"), FAVORITES("Избранное"), WATCHED("Наблюдение")
}

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
    val appSettings by remember(context) { AppSettings(context.applicationContext).settingsFlow }.collectAsState(initial = AppSettingsData())
    var showExportMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(DeviceFilter.ALL) }
    var selectedDeviceIp by remember { mutableStateOf<String?>(null) }

    val onlineCount = devices.count { it.isOnline }
    val filteredDevices = remember(devices, searchQuery, selectedFilter) {
        devices.asSequence()
            .filter { device ->
                when (selectedFilter) {
                    DeviceFilter.ALL -> true
                    DeviceFilter.ONLINE -> device.isOnline
                    DeviceFilter.OFFLINE -> !device.isOnline
                    DeviceFilter.FAVORITES -> device.isFavorite
                    DeviceFilter.WATCHED -> device.isWatched
                }
            }
            .filter { device ->
                val query = searchQuery.trim()
                query.isBlank() || listOf(device.displayName, device.ip, device.mac, device.hostname, device.vendor)
                    .any { it.contains(query, ignoreCase = true) }
            }
            .sortedWith(compareByDescending<Device> { it.isOnline }.thenBy { it.displayName.lowercase() })
            .toList()
    }
    LaunchedEffect(filteredDevices) {
        if (filteredDevices.none { it.ip == selectedDeviceIp }) {
            selectedDeviceIp = filteredDevices.firstOrNull()?.ip
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Устройства", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("СЕТЕВОЙ ИНВЕНТАРЬ", style = MaterialTheme.typography.labelSmall, color = Primary)
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
                                Icon(Icons.Default.IosShare, contentDescription = "Экспорт устройств", tint = TextPrimary)
                            }
                            DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Экспорт в TXT") },
                                    onClick = {
                                        showExportMenu = false
                                        val intent = ExportHelper.exportDevicesAsText(context, devices, appSettings.maskNetworkIdentifiers)
                                        if (intent != null) context.startActivity(Intent.createChooser(intent, "Экспорт устройств"))
                                        else Toast.makeText(context, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Экспорт в CSV") },
                                    onClick = {
                                        showExportMenu = false
                                        val intent = ExportHelper.exportDevicesAsCsv(context, devices, appSettings.maskNetworkIdentifiers)
                                        if (intent != null) context.startActivity(Intent.createChooser(intent, "Экспорт устройств"))
                                        else Toast.makeText(context, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.startScan() }, enabled = !state.isScanning) {
                        if (state.isScanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        else Icon(Icons.Default.Radar, contentDescription = "Сканировать сеть", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
        val isWideLayout = maxWidth >= 840.dp
        Row(
            modifier = Modifier.widthIn(max = 1440.dp).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(if (isWideLayout) 16.dp else 0.dp)
        ) {
        LazyColumn(
            modifier = Modifier.weight(if (isWideLayout) 1.15f else 1f).fillMaxHeight(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                PageHeading(
                    title = "Кто в вашей сети",
                    subtitle = "Обнаруженные устройства и их текущее состояние",
                    eyebrow = "DEVICE DISCOVERY"
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    DeviceMetric(value = devices.size.toString(), label = "Всего", modifier = Modifier.weight(1f))
                    DeviceMetric(value = onlineCount.toString(), label = "Онлайн", accent = Success, modifier = Modifier.weight(1f))
                    DeviceMetric(value = (devices.size - onlineCount).toString(), label = "Не подтверждено", accent = TextSecondary, modifier = Modifier.weight(1f))
                }
            }
            item {
                ContentPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Покрытие сканирования", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                if (state.scanCoverage.totalUsableAddresses > 0L) "${state.scanCoverage.coveragePercent}%" else "Нет данных",
                                style = MaterialTheme.typography.labelLarge,
                                color = Primary
                            )
                        }
                        if (state.scanCoverage.totalUsableAddresses > 0L) {
                            LinearProgressIndicator(
                                progress = { state.scanCoverage.coveragePercent.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = Primary,
                                trackColor = SurfaceElevated
                            )
                            Text(
                                "${state.scanCoverage.checkedAddresses} из ${state.scanCoverage.totalUsableAddresses} адресов проверено",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                "Источники: ARP ${state.scanCoverage.arpObservedHosts} · TCP ${state.scanCoverage.tcpResponsiveHosts} · mDNS ${state.scanCoverage.dnsSdResolvedHosts}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                            if (state.scanCoverage.arpAvailable == false) {
                                StatusBadge("ARP недоступен", StatusTone.Warning)
                                Text(
                                    "Android не предоставил доступ к таблице соседей. Сканирование продолжает использовать TCP и mDNS.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            Text("Запустите сканирование, чтобы получить данные о покрытии сети.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        if (state.scanCoverage.truncated) StatusBadge("Ограниченное покрытие", StatusTone.Warning)
                        if (
                            state.scanCoverage.scannedAt > 0L &&
                            System.currentTimeMillis() >= state.scanCoverage.scannedAt &&
                            System.currentTimeMillis() - state.scanCoverage.scannedAt > 5 * 60 * 1000L
                        ) {
                            DataQualityNotice(
                                title = "Результаты могут быть устаревшими",
                                description = "Обновите инвентарь, чтобы проверить текущее состояние устройств.",
                                tone = DataNoticeTone.Warning
                            )
                        }
                        if (state.isScanning) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Primary)
                                Text("Сканирование сети выполняется…", style = MaterialTheme.typography.labelMedium, color = Primary)
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Имя, IP, MAC или производитель") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                        }
                    },
                    shape = LanGhostShapes.control,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Primary
                    )
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DeviceFilter.entries) { filter ->
                        val count = when (filter) {
                            DeviceFilter.ALL -> devices.size
                            DeviceFilter.ONLINE -> onlineCount
                            DeviceFilter.OFFLINE -> devices.size - onlineCount
                            DeviceFilter.FAVORITES -> devices.count { it.isFavorite }
                            DeviceFilter.WATCHED -> devices.count { it.isWatched }
                        }
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text("${filter.label} · ${count}") },
                            leadingIcon = if (selectedFilter == filter) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.16f),
                                selectedLabelColor = Primary,
                                selectedLeadingIconColor = Primary
                            )
                        )
                    }
                }
            }
            item { SectionHeading(title = "Найденные устройства", actionLabel = if (devices.isEmpty()) null else "${filteredDevices.size} показано") }
            if (devices.isEmpty()) {
                item {
                    EmptyState(
                        title = if (state.isScanning) "Ищем устройства" else "Сеть пока не исследована",
                        description = if (state.isScanning) "LanGhost проверяет доступные адреса. Результаты появятся здесь." else "Запустите сканирование, чтобы обнаружить устройства в локальной сети.",
                        icon = Icons.Default.Radar,
                        actionLabel = if (state.isScanning) null else "Начать сканирование",
                        onAction = if (state.isScanning) null else ({ viewModel.startScan() })
                    )
                }
            } else if (filteredDevices.isEmpty()) {
                item {
                    EmptyState(
                        title = "Ничего не найдено",
                        description = "Измените поисковый запрос или выберите другой фильтр.",
                        icon = Icons.Default.SearchOff,
                        actionLabel = "Сбросить фильтры",
                        onAction = { searchQuery = ""; selectedFilter = DeviceFilter.ALL }
                    )
                }
            } else {
                items(filteredDevices, key = { it.stableId }) { device ->
                    DeviceInventoryRow(device = device, maskIdentifiers = appSettings.maskNetworkIdentifiers, onClick = {
                        selectedDeviceIp = device.ip
                        if (!isWideLayout) onDeviceClick(device.ip)
                    })
                }
            }
        }
        if (isWideLayout) {
            val selectedDevice = filteredDevices.firstOrNull { it.ip == selectedDeviceIp }
            DevicePreviewPanel(
                device = selectedDevice,
                maskIdentifiers = appSettings.maskNetworkIdentifiers,
                onOpenFull = { selectedDevice?.let { onDeviceClick(it.ip) } },
                modifier = Modifier.weight(0.9f).fillMaxHeight().padding(top = 12.dp, bottom = 28.dp)
            )
        }
        }
        }
    }
}

@Composable
private fun DevicePreviewPanel(
    device: Device?,
    maskIdentifiers: Boolean,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier
) {
    ContentPanel(modifier = modifier) {
        if (device == null) {
            EmptyState(
                title = "Выберите устройство",
                description = "Выберите узел в списке, чтобы посмотреть его основные параметры.",
                icon = Icons.Default.Devices
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(deviceIcon(device.deviceType), contentDescription = null, tint = if (device.isOnline) AccentCyan else TextSecondary, modifier = Modifier.size(30.dp))
                    StatusBadge(
                        deviceStatusLabel(device),
                        if (device.isOnline) StatusTone.Success else StatusTone.Neutral
                    )
                }
                Text(device.displayName, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    PrivacyFormatter.ip(device.ip, maskIdentifiers),
                    style = TechnicalTypography,
                    color = Primary
                )
                HorizontalDivider(color = Divider)
                DevicePreviewFact("MAC-АДРЕС", PrivacyFormatter.mac(device.mac.ifBlank { "—" }, maskIdentifiers))
                DevicePreviewFact("ПРОИЗВОДИТЕЛЬ", device.vendor.ifBlank { "Не определён" })
                DevicePreviewFact("ТИП", deviceTypeLabel(device.deviceType))
                DevicePreviewFact("ИМЯ ХОСТА", device.hostname.ifBlank { "—" })
                DevicePreviewFact("ОТКРЫТЫЕ ПОРТЫ", if (device.openPorts.isEmpty()) "Не проверялись" else device.openPorts.joinToString(", "))
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onOpenFull,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Открыть полный профиль")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DevicePreviewFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(value, style = TechnicalTypography, color = TextPrimary)
    }
}

@Composable
private fun DeviceMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = Primary
) {
    ContentPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun DeviceInventoryRow(device: Device, maskIdentifiers: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        contentColor = TextPrimary,
        shape = LanGhostShapes.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(LanGhostShapes.control).background(if (device.isOnline) Primary.copy(alpha = 0.11f) else SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(deviceIcon(device.deviceType), contentDescription = null, tint = if (device.isOnline) AccentCyan else TextSecondary, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(device.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (device.isFavorite) Icon(Icons.Default.Star, contentDescription = "В избранном", tint = Warning, modifier = Modifier.size(14.dp))
                    if (device.isWatched) Icon(Icons.Default.Visibility, contentDescription = "Под наблюдением", tint = Primary, modifier = Modifier.size(14.dp))
                }
                Text(
                    buildString {
                        append(PrivacyFormatter.ip(device.ip, maskIdentifiers))
                        if (device.mac.isNotBlank()) append("  ·  ${PrivacyFormatter.mac(device.mac, maskIdentifiers)}")
                    },
                    style = TechnicalTypography,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StatusBadge(deviceStatusLabel(device), if (device.isOnline) StatusTone.Success else StatusTone.Neutral)
                    val role = when {
                        device.isGateway -> "Шлюз"
                        device.isLocalDevice -> "Это устройство"
                        else -> deviceTypeLabel(device.deviceType)
                    }
                    Text(role, style = MaterialTheme.typography.labelSmall, color = TextTertiary, maxLines = 1)
                }
                if (device.vendor.isNotBlank() && device.vendor != device.displayName) {
                    Text(device.vendor, style = MaterialTheme.typography.labelSmall, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Открыть устройство", tint = TextTertiary)
        }
    }
}

private fun deviceStatusLabel(device: Device): String = when {
    device.isOnline && device.discoveryEvidence == DiscoveryEvidence.NSD_SERVICE_OBSERVATION -> "Онлайн · mDNS"
    device.isOnline -> "Онлайн"
    device.discoveryEvidence == DiscoveryEvidence.ARP_OBSERVATION -> "ARP-наблюдение"
    device.discoveryEvidence == DiscoveryEvidence.GATEWAY_CONFIGURATION -> "Шлюз · не проверен"
    device.discoveryEvidence == DiscoveryEvidence.UNKNOWN -> "Не подтверждено"
    else -> "Оффлайн"
}

private fun deviceTypeLabel(type: DeviceType): String = when (type) {
    DeviceType.PHONE -> "Телефон"
    DeviceType.TABLET -> "Планшет"
    DeviceType.TV -> "Телевизор"
    DeviceType.PC -> "Компьютер"
    DeviceType.LAPTOP -> "Ноутбук"
    DeviceType.ROUTER -> "Роутер"
    DeviceType.PRINTER -> "Принтер"
    DeviceType.NAS -> "NAS"
    DeviceType.CAMERA -> "Камера"
    DeviceType.IOT -> "IoT"
    DeviceType.SERVER -> "Сервер"
    DeviceType.UNKNOWN -> "Тип не определён"
}

private fun deviceIcon(type: DeviceType) = when (type) {
    DeviceType.PHONE, DeviceType.TABLET -> Icons.Default.PhoneAndroid
    DeviceType.TV -> Icons.Default.Tv
    DeviceType.PC, DeviceType.LAPTOP -> Icons.Default.Computer
    DeviceType.ROUTER -> Icons.Default.Router
    DeviceType.PRINTER -> Icons.Default.Print
    DeviceType.NAS, DeviceType.SERVER -> Icons.Default.Storage
    DeviceType.CAMERA -> Icons.Default.Videocam
    DeviceType.IOT -> Icons.Default.Memory
    DeviceType.UNKNOWN -> Icons.Default.DevicesOther
}
