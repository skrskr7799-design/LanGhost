package com.langhost.ui.screens.wol

import android.widget.Toast
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
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.PrivacyFormatter
import com.langhost.data.WakeOnLan
import com.langhost.data.WolProfile
import com.langhost.data.WolProfiles
import com.langhost.ui.theme.*
import com.langhost.ui.theme.TechnicalTypography
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WolProfilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WolProfiles(context) }
    val appSettings by remember(context) { AppSettings(context.applicationContext).settingsFlow }.collectAsState(initial = AppSettingsData())
    val profiles by store.profilesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var broadcast by remember { mutableStateOf("255.255.255.255") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WoL профили", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        if (profiles.isEmpty() && !showAdd) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Power, null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                    Text("Нет сохранённых профилей", color = TextSecondary)
                    Text("Сохраните MAC выключенных ПК для пробуждения", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        Text("Добавить")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(profiles, key = { it.id }) { p ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.72f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text("${PrivacyFormatter.mac(p.mac, appSettings.maskNetworkIdentifiers)} → ${PrivacyFormatter.ip(p.broadcastIp, appSettings.maskNetworkIdentifiers)}:${p.port}", style = TechnicalTypography, color = TextSecondary)
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    val ok = WakeOnLan.send(p.mac, p.broadcastIp, p.port)
                                    Toast.makeText(
                                        context,
                                        if (ok) "Пакет отправлен: ${p.name}" else "Ошибка отправки",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }) {
                                Icon(Icons.Default.Power, contentDescription = "Wake", tint = Primary)
                            }
                            IconButton(onClick = {
                                scope.launch { store.remove(p.id) }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Danger)
                            }
                        }
                    }
                }
            }
        }

        if (showAdd) {
            AlertDialog(
                onDismissRequest = { showAdd = false },
                title = { Text("Новый WoL профиль") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя") }, singleLine = true)
                        OutlinedTextField(value = mac, onValueChange = { mac = it }, label = { Text("MAC") }, singleLine = true)
                        OutlinedTextField(value = broadcast, onValueChange = { broadcast = it }, label = { Text("Broadcast IP") }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank() && mac.isNotBlank()) {
                            scope.launch {
                                store.add(
                                    WolProfile(
                                        id = UUID.randomUUID().toString(),
                                        name = name.trim(),
                                        mac = mac.trim().uppercase(),
                                        broadcastIp = broadcast.trim().ifBlank { "255.255.255.255" }
                                    )
                                )
                                name = ""; mac = ""; broadcast = "255.255.255.255"
                                showAdd = false
                            }
                        }
                    }) { Text("Сохранить") }
                },
                dismissButton = {
                    TextButton(onClick = { showAdd = false }) { Text("Отмена") }
                }
            )
        }
    }
}
