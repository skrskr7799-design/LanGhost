package com.langhost.ui.screens.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.ui.components.PageHeading
import com.langhost.ui.components.SectionHeading
import com.langhost.ui.theme.AccentCyan
import com.langhost.ui.theme.Background
import com.langhost.ui.theme.Border
import com.langhost.ui.theme.Primary
import com.langhost.ui.theme.Surface as PanelSurface
import com.langhost.ui.theme.SurfaceElevated
import com.langhost.ui.theme.SurfaceHighlight
import com.langhost.ui.theme.TextPrimary
import com.langhost.ui.theme.TextSecondary
import com.langhost.ui.theme.TextTertiary

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    onSecurity: () -> Unit,
    onUtilities: () -> Unit,
    onChannels: () -> Unit,
    onPublicIp: () -> Unit,
    onProfiles: () -> Unit,
    onWakeOnLan: () -> Unit,
    onWifi: () -> Unit,
    onSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Центр операций", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        },
        containerColor = Background
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            val wide = maxWidth >= 840.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 28.dp else 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                PageHeading(
                    title = "Инструменты сети",
                    subtitle = "Операции, диагностика и контроль локальной инфраструктуры",
                    eyebrow = "LANGHOST / OPERATIONS"
                )

                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                        DiagnosticsFeature(onUtilities, onPublicIp, Modifier.weight(1.15f))
                        Column(modifier = Modifier.weight(0.85f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeading("Безопасность и радиоэфир")
                            ToolAction("Аудит безопасности", "Открытые порты и обнаруженные риски", Icons.Default.Security, onSecurity)
                            ToolAction("Анализ каналов", "Загрузка и пересечение Wi-Fi каналов", Icons.Default.BarChart, onChannels)
                            ToolAction("Соседние сети", "Сигнал, диапазон и параметры точек доступа", Icons.Default.WifiFind, onWifi)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                        ToolGroup(
                            title = "Управление устройствами",
                            modifier = Modifier.weight(1f),
                            items = listOf(
                                Triple("Wake-on-LAN", "Отправка Magic Packet устройствам", Icons.Default.Power, onWakeOnLan),
                                Triple("Профили сети", "Сохранённые сетевые конфигурации", Icons.Default.Folder, onProfiles)
                            )
                        )
                        ToolGroup(
                            title = "Конфигурация",
                            modifier = Modifier.weight(1f),
                            items = listOf(
                                Triple("Настройки", "Мониторинг, приватность и отчёты", Icons.Default.Settings, onSettings)
                            )
                        )
                    }
                } else {
                    DiagnosticsFeature(onUtilities, onPublicIp)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeading("Безопасность и радиоэфир")
                        ToolAction("Аудит безопасности", "Открытые порты и обнаруженные риски", Icons.Default.Security, onSecurity)
                        ToolAction("Анализ каналов", "Загрузка и пересечение Wi-Fi каналов", Icons.Default.BarChart, onChannels)
                        ToolAction("Соседние сети", "Сигнал, диапазон и параметры точек доступа", Icons.Default.WifiFind, onWifi)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeading("Управление и конфигурация")
                        ToolAction("Wake-on-LAN", "Удалённое пробуждение устройств", Icons.Default.Power, onWakeOnLan)
                        ToolAction("Профили сети", "Сохранённые сетевые конфигурации", Icons.Default.Folder, onProfiles)
                        ToolAction("Настройки", "Мониторинг, приватность и отчёты", Icons.Default.Settings, onSettings)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DiagnosticsFeature(
    onUtilities: () -> Unit,
    onPublicIp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = PanelSurface,
        border = BorderStroke(1.dp, Border.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .backgroundBrush()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(color = AccentCyan.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = AccentCyan, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Text("01 / CORE DIAGNOSTICS", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.SemiBold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Проверить сеть", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Проверка доступности узлов, DNS-разрешения и внешнего сетевого адреса.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onUtilities,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("Ping / DNS", maxLines = 1) }
                Button(
                    onClick = onPublicIp,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = TextPrimary)
                ) { Text("Внешняя сеть", maxLines = 1) }
            }
            Text("Локальные проверки запускаются по вашему запросу", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
}

@Composable
private fun Modifier.backgroundBrush(): Modifier = this.then(
    Modifier
)

@Composable
private fun ToolGroup(
    title: String,
    modifier: Modifier = Modifier,
    items: List<Triple<String, String, ImageVector, () -> Unit>>
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(title)
        items.forEach { (name, description, icon, action) ->
            ToolAction(name, description, icon, action)
        }
    }
}

@Composable
private fun ToolAction(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = PanelSurface),
        border = BorderStroke(1.dp, Border.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(color = SurfaceHighlight.copy(alpha = 0.58f), shape = RoundedCornerShape(12.dp)) {
                Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.padding(10.dp).size(21.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Text("↗", style = MaterialTheme.typography.titleMedium, color = TextTertiary)
        }
    }
}
