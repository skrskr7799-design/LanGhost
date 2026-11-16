package com.langhost.ui.screens.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.ProfileConnectionMatch
import com.langhost.data.matchNetworkProfile
import com.langhost.data.NetworkProfile
import com.langhost.data.NetworkProfiles
import com.langhost.data.NetworkRepository
import com.langhost.data.PrivacyFormatter
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
fun NetworkProfilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NetworkProfiles(context) }
    val repo = remember { NetworkRepository.getInstance(context) }
    val appSettings by remember(context) { AppSettings(context.applicationContext).settingsFlow }
        .collectAsState(initial = AppSettingsData())
    val profiles by store.profilesFlow.collectAsState(initial = emptyList())
    val activeId by store.activeIdFlow.collectAsState(initial = null)
    val network by repo.networkInfo.collectAsState()
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = profiles.firstOrNull { it.id == selectedId }
        ?: profiles.firstOrNull { it.id == activeId }
        ?: profiles.firstOrNull()
    val mask = appSettings.maskNetworkIdentifiers

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профили сетей", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        name = when {
                            network.ssid.contains("home", true) -> "Дом"
                            network.ssid.isNotBlank() && network.ssid != "—" -> network.ssid.take(20)
                            else -> "Дом"
                        }
                        showAdd = true
                    }) { Icon(Icons.Default.Add, "Добавить", tint = TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    Column(modifier = Modifier.weight(1.05f).fillMaxHeight()) {
                        PageHeading(
                            title = "Сетевые профили",
                            subtitle = "Сохраняйте знакомые сети и быстро переключайте контекст",
                            eyebrow = "NETWORK CONTEXT"
                        )
                        Spacer(Modifier.height(22.dp))
                        CurrentNetworkPanel(
                            ssid = network.ssid,
                            bssid = PrivacyFormatter.bssid(network.bssid, mask),
                            isConnected = network.isConnected
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("СОХРАНЁННЫЕ", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                            Spacer(Modifier.weight(1f))
                            Text("${profiles.size}", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                        }
                        Spacer(Modifier.height(10.dp))
                        if (profiles.isEmpty()) {
                            EmptyState(
                                title = "Пока нет профилей",
                                description = "Добавьте текущую сеть, чтобы закрепить её как Дом, Работа или другое место.",
                                icon = Icons.Default.Wifi,
                                actionLabel = "Добавить профиль",
                                onAction = { showAdd = true }
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                items(profiles, key = { it.id }) { profile ->
                                    ProfileRow(
                                        profile = profile,
                                        active = profile.id == activeId,
                                        connectionMatch = matchNetworkProfile(profile, network),
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
                            ProfileDetailPanel(
                                profile = selected,
                                active = selected.id == activeId,
                                connectionMatch = matchNetworkProfile(selected, network),
                                mask = mask,
                                onActivate = { scope.launch { store.setActive(selected.id) } },
                                onDelete = {
                                    scope.launch {
                                        store.remove(selected.id)
                                        selectedId = null
                                    }
                                }
                            )
                        } else {
                            ContentPanel(modifier = Modifier.fillMaxWidth()) {
                                EmptyState(
                                    title = "Выберите профиль",
                                    description = "Здесь появятся привязка к точке доступа и действия для выбранной сети.",
                                    icon = Icons.Default.Wifi
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
                            title = "Сетевые профили",
                            subtitle = "Сохраняйте знакомые сети и быстро переключайте контекст",
                            eyebrow = "NETWORK CONTEXT"
                        )
                    }
                    item {
                        CurrentNetworkPanel(
                            ssid = network.ssid,
                            bssid = PrivacyFormatter.bssid(network.bssid, mask),
                            isConnected = network.isConnected
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("СОХРАНЁННЫЕ ПРОФИЛИ", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                            Spacer(Modifier.weight(1f))
                            Text("${profiles.size}", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                        }
                    }
                    if (profiles.isEmpty()) {
                        item {
                            EmptyState(
                                title = "Пока нет профилей",
                                description = "Добавьте текущую сеть, чтобы закрепить её как Дом, Работа или другое место.",
                                icon = Icons.Default.Wifi,
                                actionLabel = "Добавить профиль",
                                onAction = { showAdd = true }
                            )
                        }
                    }
                    items(profiles, key = { it.id }) { profile ->
                        ProfileRow(
                            profile = profile,
                            active = profile.id == activeId,
                            connectionMatch = connectionMatch(profile, network),
                            selected = false,
                            mask = mask,
                            onClick = { scope.launch { store.setActive(profile.id) } },
                            onDelete = { scope.launch { store.remove(profile.id) } }
                        )
                    }
                }
            }
        }

        if (showAdd) {
            AlertDialog(
                onDismissRequest = { showAdd = false },
                title = { Text("Новый профиль") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Название (Дом, Работа…)") },
                            singleLine = true
                        )
                        Text(
                            "Привязка к текущей сети: ${network.ssid.ifBlank { "—" }} · ${PrivacyFormatter.bssid(network.bssid, mask)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            scope.launch {
                                val profile = NetworkProfile(
                                    id = UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    ssid = network.ssid,
                                    bssid = network.bssid
                                )
                                store.add(profile)
                                selectedId = profile.id
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
private fun CurrentNetworkPanel(ssid: String, bssid: String, isConnected: Boolean) {
    ContentPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = Primary.copy(alpha = 0.12f), shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.Default.Wifi, null, tint = AccentCyan, modifier = Modifier.padding(13.dp).size(23.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ТЕКУЩЕЕ ПОДКЛЮЧЕНИЕ", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Text(ssid.ifBlank { "Сеть не определена" }, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(bssid.ifBlank { "BSSID недоступен" }, style = TechnicalTypography, color = TextSecondary)
            }
            StatusBadge(if (isConnected) "CONNECTED" else "OFFLINE", if (isConnected) StatusTone.Success else StatusTone.Neutral)
        }
    }
}

@Composable
private fun ProfileRow(
    profile: NetworkProfile,
    active: Boolean,
    connectionMatch: ProfileConnectionMatch,
    selected: Boolean,
    mask: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (selected || active) Primary.copy(alpha = 0.12f) else Surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) Primary.copy(alpha = 0.55f) else Border.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 15.dp, top = 13.dp, bottom = 13.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile.name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    listOf(profile.ssid, PrivacyFormatter.bssid(profile.bssid, mask))
                        .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Без привязки к точке доступа" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
            if (active) StatusBadge("КОНТЕКСТ", StatusTone.Info)
            if (connectionMatch == ProfileConnectionMatch.Exact) StatusBadge("ПОДКЛЮЧЕНО", StatusTone.Success)
            if (connectionMatch == ProfileConnectionMatch.SsidOnly) StatusBadge("SSID СОВПАЛ", StatusTone.Neutral)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Удалить", tint = Danger) }
        }
    }
}

@Composable
private fun ProfileDetailPanel(
    profile: NetworkProfile,
    active: Boolean,
    connectionMatch: ProfileConnectionMatch,
    mask: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    ContentPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PROFILE / DETAIL", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                Spacer(Modifier.weight(1f))
                StatusBadge(when { connectionMatch == ProfileConnectionMatch.Exact -> "ПОДКЛЮЧЕНО"; connectionMatch == ProfileConnectionMatch.SsidOnly -> "SSID СОВПАЛ"; active -> "КОНТЕКСТ"; else -> "СОХРАНЁН" }, when { connectionMatch == ProfileConnectionMatch.Exact -> StatusTone.Success; active -> StatusTone.Info; else -> StatusTone.Neutral })
            }
            Text(profile.name, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Border.copy(alpha = 0.7f))
            ProfileValue("SSID", profile.ssid.ifBlank { "Не задан" })
            ProfileValue("BSSID", PrivacyFormatter.bssid(profile.bssid, mask).ifBlank { "Не задан" }, technical = true)
            Text(
                when (connectionMatch) { ProfileConnectionMatch.Exact -> "Профиль совпадает с текущей сетью по SSID и BSSID. Это наблюдаемое подключение, а не команда переключения Wi-Fi."; ProfileConnectionMatch.SsidOnly -> "SSID совпадает, но BSSID недоступен для точного подтверждения точки доступа."; ProfileConnectionMatch.None -> "Профиль не подтверждён текущим подключением. Выбор контекста приложения не меняет системное подключение Wi-Fi." },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.weight(1f, fill = false))
            if (!active) {
                Button(onClick = onActivate, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Text("Выбрать контекст")
                }
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("Удалить профиль")
            }
        }
    }
}

@Composable
private fun ProfileValue(label: String, value: String, technical: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(value, style = if (technical) TechnicalTypography else MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}


