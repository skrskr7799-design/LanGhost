package com.langhost.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.langhost.data.AppLockSettings
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.NetworkRepository
import com.langhost.data.ReportShare
import com.langhost.data.SecurityAudit
import com.langhost.data.WatchWorker
import com.langhost.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appSettings: AppSettings,
    private val lockSettings: AppLockSettings
) : ViewModel() {
    val settings: StateFlow<AppSettingsData> = appSettings.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsData())

    val biometricEnabled = lockSettings.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val hideSensitive = lockSettings.hideSensitive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setWatchMode(enabled: Boolean) = viewModelScope.launch { appSettings.setWatchMode(enabled) }
    fun setNotifyNew(enabled: Boolean) = viewModelScope.launch { appSettings.setNotifyNewDevices(enabled) }
    fun setAutoScan(enabled: Boolean) = viewModelScope.launch { appSettings.setAutoScanOnOpen(enabled) }
    fun setBiometric(v: Boolean) = viewModelScope.launch { lockSettings.setBiometricEnabled(v) }
    fun setHideSensitive(v: Boolean) = viewModelScope.launch { lockSettings.setHideSensitive(v) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                return SettingsViewModel(AppSettings(app), AppLockSettings(app)) as T
            }
        }
    )
    val settings by viewModel.settings.collectAsState()
    val biometric by viewModel.biometricEnabled.collectAsState()
    val hideSensitive by viewModel.hideSensitive.collectAsState()
    val scope = rememberCoroutineScope()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setNotifyNew(true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection("Мониторинг") {
                SettingsSwitch(
                    "Режим наблюдения",
                    "Фоновое сканирование ~15 мин",
                    settings.watchModeEnabled
                ) { enabled ->
                    viewModel.setWatchMode(enabled)
                    if (enabled) {
                        WatchWorker.start(context)
                    } else {
                        WatchWorker.stop(context)
                    }
                }
                SettingsSwitch("Уведомления о новых устройствах", "Требуется разрешение на Android 13+", settings.notifyNewDevices) { enabled ->
                    if (!enabled) {
                        viewModel.setNotifyNew(false)
                    } else if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setNotifyNew(true)
                    }
                }
            }

            SettingsSection("Сканирование") {
                SettingsSwitch("Автосканирование при запуске", "Сразу искать устройства", settings.autoScanOnOpen) {
                    viewModel.setAutoScan(it)
                }
            }

            SettingsSection("Приватность") {
                SettingsSwitch("Блокировка приложения", "Биометрия / PIN при входе", biometric) {
                    viewModel.setBiometric(it)
                }
                SettingsSwitch("Скрывать MAC и IP", "Для скриншотов (режим приватности)", hideSensitive) {
                    viewModel.setHideSensitive(it)
                }
            }

            SettingsSection("Отчёт") {
                Button(
                    onClick = {
                        scope.launch {
                            val repo = NetworkRepository.getInstance(context)
                            val devices = repo.devices.value
                            val net = repo.networkInfo.value
                            val score = SecurityAudit.analyze(devices, net.ssid, net.bssid, emptyList()).score
                            val text = ReportShare.buildTextReport(net, devices, score)
                            val img = ReportShare.shareImageCard(context, net, devices, score)
                            if (img != null) {
                                context.startActivity(Intent.createChooser(img, "Поделиться отчётом"))
                            } else {
                                context.startActivity(Intent.createChooser(ReportShare.shareText(context, text), "Поделиться"))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Поделиться карточкой отчёта")
                }
            }

            SettingsSection("О приложении") {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LanGhost", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Версия 0.4.0", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text("Локальный сетевой разведчик.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        content()
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
