package com.langhost.ui.screens.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import com.langhost.data.WakeOnLan
import com.langhost.ui.theme.*
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
    val scope = rememberCoroutineScope()
    val viewModel: DeviceDetailViewModel = viewModel(
        key = deviceIp,
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
    var wolSending by remember { mutableStateOf(false) }

    fun copyToClipboard(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "$label скопирован", Toast.LENGTH_SHORT).show()
    }

    fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return "—"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("ru"))
        return sdf.format(Date(timestamp))
    }

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Избранное",
                            tint = if (state.isFavorite) Secondary else TextPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.toggleWatched() }) {
                        Icon(
                            imageVector = if (state.isWatched) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Наблюдение",
                            tint = if (state.isWatched) Primary else TextPrimary
                        )
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Информация об устройстве", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    HorizontalDivider(color = Divider)

                    CopyableInfoRow("IP-адрес", device.ip) { copyToClipboard("IP", device.ip) }
                    CopyableInfoRow("MAC-адрес", device.mac.ifBlank { "—" }) {
                        if (device.mac.isNotBlank()) copyToClipboard("MAC", device.mac)
                    }

                    InfoRow("Имя хоста", device.hostname.ifBlank { "—" })
                    InfoRow("Производитель", device.vendor.ifBlank { "Неизвестно" })
                    InfoRow("ОС (предположение)", device.osGuess.ifBlank { "Неизвестно" })
                    InfoRow("Статус", if (device.isOnline) "Онлайн" else "Оффлайн")

                    when {
                        device.isGateway -> InfoRow("Роль", "Шлюз / Роутер")
                        device.isLocalDevice -> InfoRow("Роль", "Это устройство")
                    }

                    HorizontalDivider(color = Divider)
                    InfoRow("Впервые замечен", formatTime(device.firstSeen))
                    InfoRow("Последний раз", formatTime(device.lastSeen))
                }
            }

            // Actions: WoL
            if (device.mac.isNotBlank() && !device.isLocalDevice) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Действия", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Button(
                            onClick = {
                                scope.launch {
                                    wolSending = true
                                    val ok = WakeOnLan.send(device.mac)
                                    wolSending = false
                                    Toast.makeText(
                                        context,
                                        if (ok) "Magic packet отправлен" else "Не удалось отправить",
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                            Icon(Icons.Default.Power, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (wolSending) "Отправка..." else "Wake-on-LAN")
                        }
                        Text(
                            text = "Отправляет magic packet для включения устройства (если поддерживается)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }

            // Port Scanner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Сканер портов", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Button(
                            onClick = { viewModel.scanPorts() },
                            enabled = !state.isScanningPorts,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            if (state.isScanningPorts) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OnPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Сканирование...")
                            } else {
                                Text("Сканировать")
                            }
                        }
                    }

                    if (state.openPorts.isNotEmpty()) {
                        Text("Открытые порты (${state.openPorts.size}):", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(
                            text = state.openPorts.joinToString(", "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Success,
                            fontWeight = FontWeight.Medium
                        )
                        val serviceHints = state.openPorts.mapNotNull { port ->
                            val name = when (port) {
                                21 -> "FTP"; 22 -> "SSH"; 23 -> "Telnet"; 25 -> "SMTP"; 53 -> "DNS"
                                80 -> "HTTP"; 110 -> "POP3"; 135 -> "RPC"; 139, 445 -> "SMB"; 143 -> "IMAP"
                                443 -> "HTTPS"; 548 -> "AFP"; 993 -> "IMAPS"; 995 -> "POP3S"
                                1723 -> "PPTP"; 3306 -> "MySQL"; 3389 -> "RDP"; 5900 -> "VNC"
                                8080, 8443 -> "HTTP-Alt"; 9100 -> "Принтер"; else -> null
                            }
                            name?.let { "$port ($it)" }
                        }
                        if (serviceHints.isNotEmpty()) {
                            Text(serviceHints.joinToString("  •  "), style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        }
                    } else if (!state.isScanningPorts) {
                        Text("Открытых портов не найдено или сканирование не выполнялось", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
            }

            // Customization
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Персонализация", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

                    OutlinedTextField(
                        value = state.nickname,
                        onValueChange = { viewModel.updateNickname(it) },
                        label = { Text("Кличка") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = Border,
                            focusedLabelColor = Primary, cursorColor = Primary
                        )
                    )

                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = { viewModel.updateNotes(it) },
                        label = { Text("Заметки") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = Border,
                            focusedLabelColor = Primary, cursorColor = Primary
                        )
                    )

                    Button(
                        onClick = { viewModel.saveCustomization() },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text(if (state.isSaving) "Сохранение..." else "Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CopyableInfoRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            if (value != "—") {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", tint = TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
