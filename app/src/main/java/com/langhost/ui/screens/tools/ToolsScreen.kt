package com.langhost.ui.screens.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.ui.components.PageHeading
import com.langhost.ui.components.SectionHeading
import com.langhost.ui.theme.AccentCyan
import com.langhost.ui.theme.Background
import com.langhost.ui.theme.Border
import com.langhost.ui.theme.Primary
import com.langhost.ui.theme.Surface
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
                title = { Text("Центр инструментов", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            PageHeading(
                title = "Инструменты сети",
                subtitle = "Диагностика, анализ и управление локальной инфраструктурой",
                eyebrow = "OPERATIONS TOOLKIT"
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading(title = "Анализ и безопасность")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ToolCard("Аудит безопасности", "Риски и открытые порты", Icons.Default.Security, onSecurity, Modifier.weight(1f))
                    ToolCard("Анализ каналов", "Загрузка Wi-Fi эфира", Icons.Default.BarChart, onChannels, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ToolCard("Соседние сети", "Сигнал, диапазоны, каналы", Icons.Default.WifiFind, onWifi, Modifier.weight(1f))
                    ToolCard("Ping и DNS", "Проверка доступности", Icons.Default.Build, onUtilities, Modifier.weight(1f))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading(title = "Подключение и управление")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ToolCard("Публичный IP", "Внешний адрес и сеть", Icons.Default.Public, onPublicIp, Modifier.weight(1f))
                    ToolCard("Wake-on-LAN", "Удалённое пробуждение", Icons.Default.Power, onWakeOnLan, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ToolCard("Профили сети", "Сохранённые конфигурации", Icons.Default.Folder, onProfiles, Modifier.weight(1f))
                    ToolCard("Настройки", "Поведение и приватность", Icons.Default.Settings, onSettings, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Border.copy(alpha = 0.78f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Surface(
                    color = Primary.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.padding(9.dp).size(20.dp))
                }
                Text("OPEN", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
