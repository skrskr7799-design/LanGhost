package com.langhost.ui.screens.wol

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.PrivacyFormatter
import com.langhost.data.WakeOnLan
import com.langhost.data.WolProfile
import com.langhost.data.WolProfiles
import com.langhost.ui.components.ContentPanel
import com.langhost.ui.components.EmptyState
import com.langhost.ui.components.PageHeading
import com.langhost.ui.components.StatusBadge
import com.langhost.ui.components.StatusTone
import com.langhost.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WolProfilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WolProfiles(context) }
    val appSettings by remember(context) { AppSettings(context.applicationContext).settingsFlow }
        .collectAsState(initial = AppSettingsData())
    val profiles by store.profilesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var broadcast by remember { mutableStateOf("255.255.255.255") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    val mask = appSettings.maskNetworkIdentifiers

    fun wake(profile: WolProfile) {
        scope.launch {
            val ok = WakeOnLan.send(profile.mac, profile.broadcastIp, profile.port)
            Toast.makeText(
                context,
                if (ok) "Пакет отправлен: ${profile.name}" else "Ошибка отправки",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wake-on-LAN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Power, "Добавить устройство", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    Column(modifier = Modifier.weight(1.05f).fillMaxHeight()) {
                        PageHeading(
                            title = "Пробуждение устройств",
                            subtitle = "Отправка Magic Packet сохранённым устройствам в локальной сети",
                            eyebrow = "REMOTE POWER"
                        )
                        Spacer(Modifier.height(22.dp))
                        ContentPanel {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Router, null, tint = AccentCyan, modifier = Modifier.size(26.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("MAGIC PACKET", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                                    Text("UDP · порт 9", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                }
                                StatusBadge("LOCAL NETWORK", StatusTone.Info)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("СОХРАНЁННЫЕ УСТРОЙСТВА", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                            Spacer(Modifier.weight(1f))
                            Text("${profiles.size}", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                        }
                        Spacer(Modifier.height(10.dp))
                        if (profiles.isEmpty()) {
                            EmptyState(
                                title = "Список пока пуст",
                                description = "Добавьте MAC-адрес выключенного компьютера или NAS, чтобы отправлять ему Magic Packet.",
                                icon = Icons.Default.Power,
                                actionLabel = "Добавить устройство",
                                onAction = { showAdd = true }
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                items(profiles, key = { it.id }) { profile ->
                                    WolProfileRow(
                                        profile = profile,
                                        selected = profile.id == selected?.id,
                                        mask = mask,
                                        onClick = { selectedId = profile.id },
                                        onDelete = {
                                            scope.launch {
                                                store.remove(profile.id)
                                                if (selectedId == profile.id) selectedId = null
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(0.95f).fillMaxHeight()) {
                        if (selected != null) {
                            WolDetailPanel(
                                profile = selected,
                                mask = mask,
                                onWake = { wake(selected) },
                                onDelete = {
                                    scope.launch {
                                        store.remove(selected.id)
                                        selectedId = null
                                    }
                                }
                            )
                        } else {
                            ContentPanel(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                EmptyState(
                                    title = "Выберите устройство",
                                    description = "Параметры отправки и действие пробуждения появятся здесь.",
                                    icon = Icons.Default.Power
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        PageHeading(
                            title = "Пробуждение устройств",
                            subtitle = "Отправка Magic Packet сохранённым устройствам в локальной сети",
                            eyebrow = "REMOTE POWER"
                        )
                    }
                    item {
                        ContentPanel {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Router, null, tint = AccentCyan, modifier = Modifier.size(25.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("MAGIC PACKET", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                                    Text("UDP · порт 9", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                }
                                StatusBadge("LOCAL", StatusTone.Info)
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("СОХРАНЁННЫЕ УСТРОЙСТВА", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                            Spacer(Modifier.weight(1f))
                            Text("${profiles.size}", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                        }
                    }
                    if (profiles.isEmpty()) {
                        item {
                            EmptyState(
                                title = "Список пока пуст",
                                description = "Добавьте MAC-адрес выключенного компьютера или NAS, чтобы отправлять ему Magic Packet.",
                                icon = Icons.Default.Power,
                                actionLabel = "Добавить устройство",
                                onAction = { showAdd = true }
                            )
                        }
                    }
                    items(profiles, key = { it.id }) { profile ->
                        WolProfileRow(
                            profile = profile,
                            selected = false,
                            mask = mask,
                            onClick = { selectedId = profile.id },
                            onDelete = { scope.launch { store.remove(profile.id) } },
                            onWake = { wake(profile) }
                        )
                    }
                }
            }
        }

        if (showAdd) {
            AlertDialog(
                onDismissRequest = { showAdd = false },
                title = { Text("Новое устройство") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя устройства") }, singleLine = true)
                        OutlinedTextField(value = mac, onValueChange = { mac = it }, label = { Text("MAC-адрес") }, singleLine = true)
                        OutlinedTextField(value = broadcast, onValueChange = { broadcast = it }, label = { Text("Broadcast IP") }, singleLine = true)
                        Text("Устройство должно поддерживать Wake-on-LAN, а функция — быть включена в BIOS/UEFI и сетевом адаптере.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = name.isNotBlank() && mac.isNotBlank(),
                        onClick = {
                            scope.launch {
                                val profile = WolProfile(
                                    id = UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    mac = mac.trim().uppercase(),
                                    broadcastIp = broadcast.trim().ifBlank { "255.255.255.255" }
                                )
                                store.add(profile)
                                selectedId = profile.id
                                name = ""
                                mac = ""
                                broadcast = "255.255.255.255"
                                showAdd = false
                            }
                        }
                    ) { Text("Сохранить") }
                },
                dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } }
            )
        }
    }
}

@Composable
private fun WolProfileRow(
    profile: WolProfile,
    selected: Boolean,
    mask: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onWake: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (selected) Primary.copy(alpha = 0.12f) else Surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) Primary.copy(alpha = 0.55f) else Border.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 15.dp, top = 12.dp, bottom = 12.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile.name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    PrivacyFormatter.mac(profile.mac, mask),
                    style = TechnicalTypography,
                    color = TextSecondary
                )
            }
            if (onWake != null) {
                IconButton(onClick = onWake) { Icon(Icons.Default.Power, "Пробудить", tint = Primary) }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Удалить", tint = Danger) }
        }
    }
}

@Composable
private fun WolDetailPanel(
    profile: WolProfile,
    mask: Boolean,
    onWake: () -> Unit,
    onDelete: () -> Unit
) {
    ContentPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DEVICE / POWER", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                Spacer(Modifier.weight(1f))
                StatusBadge("READY TO SEND", StatusTone.Info)
            }
            Text(profile.name, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Border.copy(alpha = 0.7f))
            WolValue("MAC ADDRESS", PrivacyFormatter.mac(profile.mac, mask))
            WolValue("BROADCAST", PrivacyFormatter.ip(profile.broadcastIp, mask))
            WolValue("UDP PORT", profile.port.toString())
            Text(
                "LanGhost отправит Magic Packet на указанный broadcast-адрес. Успешная отправка пакета не гарантирует, что устройство включилось.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Button(
                onClick = onWake,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Default.Power, null)
                Spacer(Modifier.width(9.dp))
                Text("Отправить Magic Packet")
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("Удалить устройство")
            }
        }
    }
}

@Composable
private fun WolValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(value, style = TechnicalTypography, color = TextPrimary)
    }
}
