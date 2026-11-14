package com.langhost.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.WatchWorker
import com.langhost.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val appSettings: AppSettings) : ViewModel() {
    val settings: StateFlow<AppSettingsData> = appSettings.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsData())

    fun setWatchMode(enabled: Boolean) = viewModelScope.launch { appSettings.setWatchMode(enabled) }
    fun setNotifyNew(enabled: Boolean) = viewModelScope.launch { appSettings.setNotifyNewDevices(enabled) }
    fun setAutoScan(enabled: Boolean) = viewModelScope.launch { appSettings.setAutoScanOnOpen(enabled) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(AppSettings(context.applicationContext)) as T
            }
        }
    )

    val settings by viewModel.settings.collectAsState()

    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Настройки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(title = "Мониторинг") {
                SettingsSwitch(
                    title = "Режим наблюдения",
                    subtitle = "Периодическое сканирование сети в фоне (каждые ~15 мин)",
                    checked = settings.watchModeEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setWatchMode(enabled)
                        if (enabled) {
                            if (notificationPermission != null && !notificationPermission.status.allPermissionsGranted) {
                                notificationPermission.launchPermissionRequest()
                            }
                            WatchWorker.start(context)
                        } else {
                            WatchWorker.stop(context)
                        }
                    }
                )
                SettingsSwitch(
                    title = "Уведомления о новых устройствах",
                    subtitle = "Сообщать, когда появляется неизвестный хост",
                    checked = settings.notifyNewDevices,
                    onCheckedChange = { viewModel.setNotifyNew(it) }
                )
            }

            SettingsSection(title = "Сканирование") {
                SettingsSwitch(
                    title = "Автосканирование при запуске",
                    subtitle = "Сразу искать устройства при открытии приложения",
                    checked = settings.autoScanOnOpen,
                    onCheckedChange = { viewModel.setAutoScan(it) }
                )
            }

            SettingsSection(title = "О приложении") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LanGhost", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Версия 0.2.0", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(
                            "Локальный сетевой разведчик. Агрессивный сбор информации об устройствах в вашей сети.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        content()
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnPrimary,
                    checkedTrackColor = Primary,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = SurfaceVariant
                )
            )
        }
    }
}
