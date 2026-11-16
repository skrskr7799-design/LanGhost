package com.langhost.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.compose.foundation.BorderStroke
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
import com.langhost.ui.components.PageHeading
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

    fun setWatchMode(enabled: Boolean) = viewModelScope.launch { appSettings.setWatchMode(enabled) }
    fun setNotifyNew(enabled: Boolean) = viewModelScope.launch { appSettings.setNotifyNewDevices(enabled) }
    fun setAutoScan(enabled: Boolean) = viewModelScope.launch { appSettings.setAutoScanOnOpen(enabled) }
    fun setMaskNetworkIdentifiers(enabled: Boolean) = viewModelScope.launch { appSettings.setMaskNetworkIdentifiers(enabled) }
    fun setBiometric(v: Boolean) = viewModelScope.launch { lockSettings.setBiometricEnabled(v) }
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
    val scope = rememberCoroutineScope()
    var biometricError by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setNotifyNew(true)
    }

    val monitoringBlock: @Composable () -> Unit = {
        SettingsSection("Мониторинг") {
            SettingsSwitch(
                "Режим наблюдения",
                "Фоновое сканирование примерно раз в 15 минут",
                settings.watchModeEnabled
            ) { enabled ->
                viewModel.setWatchMode(enabled)
                if (enabled) WatchWorker.start(context) else WatchWorker.stop(context)
            }
            SettingsSwitch(
                "Уведомления о новых устройствах",
                "Требуется разрешение на Android 13+",
                settings.notifyNewDevices
            ) { enabled ->
                if (!enabled) {
                    viewModel.setNotifyNew(false)
                } else if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setNotifyNew(true)
                }
            }
        }
    }

    val scanBlock: @Composable () -> Unit = {
        SettingsSection("Сканирование") {
            SettingsSwitch(
                "Автосканирование при запуске",
                "Начинать поиск устройств при открытии приложения",
                settings.autoScanOnOpen,
                viewModel::setAutoScan
            )
        }
    }

    val privacyBlock: @Composable () -> Unit = {
        SettingsSection("Приватность и доступ") {
            SettingsSwitch(
                "Скрывать сетевые идентификаторы",
                "Маскировать IP, MAC и BSSID в интерфейсе и отчётах",
                settings.maskNetworkIdentifiers,
                viewModel::setMaskNetworkIdentifiers
            )
            SettingsSwitch("Блокировка приложения", "Биометрия или PIN при входе", biometric) { enabled ->
                if (!enabled) {
                    biometricError = null
                    viewModel.setBiometric(false)
                } else {
                    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    if (BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
                        biometricError = null
                        viewModel.setBiometric(true)
                    } else {
                        biometricError = "Сначала настройте биометрию или PIN/пароль блокировки устройства."
                    }
                }
            }
            biometricError?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
        }
    }

    val reportBlock: @Composable () -> Unit = {
        SettingsSection("Отчёт") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Border.copy(alpha = 0.72f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Сводка локальной сети", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "Подготовить карточку с текущими устройствами и оценкой безопасности. Приватность учитывает настройку маскирования.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val repo = NetworkRepository.getInstance(context)
                                val devices = repo.devices.value
                                val net = repo.networkInfo.value
                                val score = SecurityAudit.analyze(devices, net.ssid, net.bssid, emptyList()).score
                                val text = ReportShare.buildTextReport(net, devices, score, settings.maskNetworkIdentifiers)
                                val img = ReportShare.shareImageCard(context, net, devices, score, settings.maskNetworkIdentifiers)
                                if (img != null) {
                                    context.startActivity(Intent.createChooser(img, "Поделиться отчётом"))
                                } else {
                                    context.startActivity(Intent.createChooser(ReportShare.shareText(context, text), "Поделиться"))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) { Text("Поделиться карточкой отчёта") }
                }
            }
        }
    }

    val aboutBlock: @Composable () -> Unit = {
        SettingsSection("О приложении") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Border.copy(alpha = 0.72f))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("LanGhost", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Версия 0.4.0", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("Локальная сетевая диагностика и наблюдение.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        PageHeading(
                            title = "Контроль приложения",
                            subtitle = "Поведение мониторинга, частота обнаружения и уведомления",
                            eyebrow = "PREFERENCES"
                        )
                        monitoringBlock()
                        scanBlock()
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        privacyBlock()
                        reportBlock()
                        aboutBlock()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    PageHeading(
                        title = "Контроль приложения",
                        subtitle = "Мониторинг, приватность и поведение LanGhost",
                        eyebrow = "PREFERENCES"
                    )
                    monitoringBlock()
                    scanBlock()
                    privacyBlock()
                    reportBlock()
                    aboutBlock()
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = AccentCyan)
        content()
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Border.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
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
