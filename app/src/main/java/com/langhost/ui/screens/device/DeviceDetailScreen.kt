package com.langhost.ui.screens.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.langhost.data.WakeOnLan
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.PrivacyFormatter
import com.langhost.data.WolProfile
import com.langhost.data.WolProfiles
import com.langhost.data.model.DeviceType
import com.langhost.ui.theme.*
import com.langhost.ui.components.ContentPanel
import com.langhost.ui.theme.TechnicalTypography
import com.langhost.ui.components.StatusBadge
import com.langhost.ui.components.StatusTone
import com.langhost.ui.util.QuickActions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceIp: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appSettings by remember(context) { AppSettings(context.applicationContext).settingsFlow }.collectAsState(initial = AppSettingsData())
    val scope = rememberCoroutineScope()
    val viewModel: DeviceDetailViewModel = viewModel(
        key = deviceIp,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DeviceDetailViewModel(
                    context.applicationContext as android.app.Application,
                    deviceIp
                ) as T
            }
        }
    )

    val state by viewModel.uiState.collectAsState()
    val device = state.device
    var wolSending by remember { mutableStateOf(false) }

    fun copyToClipboard(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val safeValue = when (label) {
            "IP" -> PrivacyFormatter.ip(value, appSettings.maskNetworkIdentifiers)
            "MAC" -> PrivacyFormatter.mac(value, appSettings.maskNetworkIdentifiers)
            else -> value
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(label, safeValue))
        Toast.makeText(context, "$label скопирован", Toast.LENGTH_SHORT).show()
    }

    fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return "—"
        return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("ru")).format(Date(timestamp))
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(device?.displayName ?: deviceIp, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(if (state.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, "Избранное", tint = if (state.isFavorite) Secondary else TextPrimary)
                    }
                    IconButton(onClick = { viewModel.toggleWatched() }) {
                        Icon(if (state.isWatched) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Наблюдение", tint = if (state.isWatched) Primary else TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        if (device == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Устройство не найдено", color = TextSecondary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ContentPanel {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        androidx.compose.material3.Surface(
                            color = if (device.isOnline) AccentCyan.copy(alpha = 0.12f) else SurfaceElevated,
                            shape = LanGhostShapes.card
                        ) {
                            Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                                Icon(deviceTypeIcon(device.deviceType), contentDescription = null, tint = if (device.isOnline) AccentCyan else TextSecondary, modifier = Modifier.size(29.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(device.displayName, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(device.vendor.ifBlank { typeLabel(device.deviceType) }, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(if (state.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Избранное", tint = if (state.isFavorite) Secondary else TextSecondary)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(if (device.isOnline) "Онлайн" else "Оффлайн", if (device.isOnline) StatusTone.Success else StatusTone.Neutral)
                        if (device.isGateway) StatusBadge("Шлюз", StatusTone.Info)
                        if (device.isLocalDevice) StatusBadge("Это устройство", StatusTone.Warning)
                        if (state.isWatched) StatusBadge("Наблюдение", StatusTone.Info)
                    }
                    HorizontalDivider(color = Divider)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("IP-АДРЕС", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                            Text(PrivacyFormatter.ip(device.ip, appSettings.maskNetworkIdentifiers), style = TechnicalTypography, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }
                        IconButton(onClick = { copyToClipboard("IP", device.ip) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Скопировать IP", tint = Primary)
                        }
                    }
                    if (device.mac.isNotBlank()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("MAC-АДРЕС", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                                Text(PrivacyFormatter.mac(device.mac, appSettings.maskNetworkIdentifiers), style = TechnicalTypography, color = TextSecondary)
                            }
                            IconButton(onClick = { copyToClipboard("MAC", device.mac) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Скопировать MAC", tint = Primary)
                            }
                        }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = LanGhostShapes.card, border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Информация об устройстве", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    HorizontalDivider(color = Divider)
                    CopyableInfoRow("IP-адрес", PrivacyFormatter.ip(device.ip, appSettings.maskNetworkIdentifiers)) { copyToClipboard("IP", device.ip) }
                    CopyableInfoRow("MAC-адрес", PrivacyFormatter.mac(device.mac.ifBlank { "—" }, appSettings.maskNetworkIdentifiers)) {
                        if (device.mac.isNotBlank()) copyToClipboard("MAC", device.mac)
                    }
                    InfoRow("Имя хоста", device.hostname.ifBlank { "—" })
                    InfoRow("Производитель", device.vendor.ifBlank { "Неизвестно" })
                    InfoRow("Тип", typeLabel(device.deviceType))
                    InfoRow("ОС (предположение)", device.osGuess.ifBlank { "Неизвестно" })
                    InfoRow("Статус", if (device.isOnline) "Онлайн" else "Оффлайн")
                    if (device.isRandomMac) InfoRow("MAC", "Рандомизированный")
                    if (device.httpFingerprint.isNotBlank()) InfoRow("HTTP", device.httpFingerprint)
                    when {
                        device.isGateway -> InfoRow("Роль", "Шлюз / Роутер")
                        device.isLocalDevice -> InfoRow("Роль", "Это устройство")
                    }
                    HorizontalDivider(color = Divider)
                    InfoRow("Впервые замечен", formatTime(device.firstSeen))
                    InfoRow("Последний раз", formatTime(device.lastSeen))
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = LanGhostShapes.card) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Быстрые действия", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { QuickActions.openHttpAdmin(context, device) }, modifier = Modifier.weight(1f)) { Text("Админка") }
                        if (22 in device.openPorts || device.openPorts.isEmpty()) {
                            OutlinedButton(onClick = { QuickActions.openSsh(context, device) }, modifier = Modifier.weight(1f)) { Text("SSH") }
                        }
                        if (3389 in device.openPorts || device.openPorts.isEmpty()) {
                            OutlinedButton(onClick = { QuickActions.openRdp(context, device) }, modifier = Modifier.weight(1f)) { Text("RDP") }
                        }
                    }
                }
            }

            PingHistorySection(ip = device.ip)

            if (device.mac.isNotBlank() && !device.isLocalDevice) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = LanGhostShapes.card) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Wake-on-LAN", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Button(
                            onClick = {
                                scope.launch {
                                    wolSending = true
                                    val ok = WakeOnLan.send(device.mac)
                                    wolSending = false
                                    Toast.makeText(context, if (ok) "Magic packet отправлен" else "Не удалось отправить", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !wolSending,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            if (wolSending) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OnPrimary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(Icons.Default.Power, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (wolSending) "Отправка..." else "Разбудить сейчас")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    WolProfiles(context).add(WolProfile(id = UUID.randomUUID().toString(), name = device.displayName, mac = device.mac))
                                    Toast.makeText(context, "Профиль WoL сохранён", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Сохранить в профили WoL") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = LanGhostShapes.card) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Сканер портов", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Button(onClick = { viewModel.scanPorts() }, enabled = !state.isScanningPorts, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                            if (state.isScanningPorts) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OnPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("...")
                            } else Text("Сканировать")
                        }
                    }
                    if (state.openPorts.isNotEmpty()) {
                        Text("Открытые порты (${state.openPorts.size}):", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(state.openPorts.joinToString(", "), style = MaterialTheme.typography.bodyLarge, color = Success, fontWeight = FontWeight.Medium)
                    } else if (!state.isScanningPorts) {
                        Text("Сканирование не выполнялось", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = LanGhostShapes.card) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Персонализация", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    OutlinedTextField(
                        value = state.nickname, onValueChange = { viewModel.updateNickname(it) },
                        label = { Text("Кличка") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Border, focusedLabelColor = Primary, cursorColor = Primary)
                    )
                    OutlinedTextField(
                        value = state.notes, onValueChange = { viewModel.updateNotes(it) },
                        label = { Text("Заметки") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Border, focusedLabelColor = Primary, cursorColor = Primary)
                    )
                    Button(onClick = { viewModel.saveCustomization() }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        Text(if (state.isSaving) "Сохранение..." else "Сохранить")
                    }
                }
            }
        }
    }
}

private fun typeLabel(type: DeviceType) = when (type) {
    DeviceType.PHONE -> "Телефон"
    DeviceType.TABLET -> "Планшет"
    DeviceType.TV -> "ТВ"
    DeviceType.PC -> "ПК"
    DeviceType.LAPTOP -> "Ноутбук"
    DeviceType.ROUTER -> "Роутер"
    DeviceType.PRINTER -> "Принтер"
    DeviceType.NAS -> "NAS"
    DeviceType.CAMERA -> "Камера"
    DeviceType.IOT -> "IoT"
    DeviceType.SERVER -> "Сервер"
    DeviceType.UNKNOWN -> "Неизвестно"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CopyableInfoRow(label: String, value: String, onCopy: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            if (value != "—") {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, "Копировать", tint = TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun deviceTypeIcon(type: DeviceType) = when (type) {
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
