package com.langhost.ui.screens.profiles

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.data.NetworkProfile
import com.langhost.data.NetworkProfiles
import com.langhost.data.NetworkRepository
import com.langhost.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkProfilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NetworkProfiles(context) }
    val repo = remember { NetworkRepository.getInstance(context) }
    val profiles by store.profilesFlow.collectAsState(initial = emptyList())
    val activeId by store.activeIdFlow.collectAsState(initial = null)
    val network by repo.networkInfo.collectAsState()
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профили сетей", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
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
                    }) {
                        Icon(Icons.Default.Add, "Добавить", tint = TextPrimary)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Текущая: ${network.ssid.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            if (profiles.isEmpty()) {
                item {
                    Text("Сохраните Home / Work / Cafe с привязкой к BSSID", color = TextTertiary)
                }
            }
            items(profiles, key = { it.id }) { p ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (p.id == activeId) Primary.copy(alpha = 0.15f) else Surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { scope.launch { store.setActive(p.id) } }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(
                                listOf(p.ssid, p.bssid).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "без привязки" },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (p.id == activeId) {
                            Text("активен", style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                        IconButton(onClick = { scope.launch { store.remove(p.id) } }) {
                            Icon(Icons.Default.Delete, "Удалить", tint = Danger)
                        }
                    }
                }
            }
        }

        if (showAdd) {
            AlertDialog(
                onDismissRequest = { showAdd = false },
                title = { Text("Новый профиль") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название (Дом, Работа…)") }, singleLine = true)
                        Text("Будет привязан к текущей сети: ${network.ssid} / ${network.bssid}", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank()) {
                            scope.launch {
                                store.add(
                                    NetworkProfile(
                                        id = UUID.randomUUID().toString(),
                                        name = name.trim(),
                                        ssid = network.ssid,
                                        bssid = network.bssid
                                    )
                                )
                                showAdd = false
                            }
                        }
                    }) { Text("Сохранить") }
                },
                dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } }
            )
        }
    }
}
