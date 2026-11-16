package com.langhost.ui.screens.internet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.data.PublicIpLookup
import com.langhost.data.PublicNetworkInfo
import com.langhost.ui.theme.*
import com.langhost.ui.theme.TechnicalTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicIpScreen(onBack: () -> Unit) {
    var info by remember { mutableStateOf(PublicNetworkInfo()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var requested by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            requested = true
            error = null
            try {
                val result = PublicIpLookup.fetch()
                if (result.ip.isBlank()) error = "Не удалось получить данные. Проверьте подключение и попробуйте ещё раз."
                info = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Не удалось получить данные. Попробуйте ещё раз."
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Внешняя сеть", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextPrimary)
                    }
                },
                actions = {
                    if (requested) {
                        TextButton(onClick = { refresh() }, enabled = !loading) {
                            Text("Обновить", color = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Перед запросом", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "По нажатию кнопки запрос отправится в ipapi.co. Сервис увидит ваш публичный IP и вернёт сведения о провайдере и примерном местоположении. Если он недоступен, LanGhost обратится к api.ipify.org только за IP-адресом. Данные локальной сети не отправляются.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Button(
                        onClick = { refresh() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = OnPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (loading) "Определяем…" else if (requested) "Повторить запрос" else "Определить внешний IP")
                    }
                }
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }

            error?.let { Text(it, color = Danger) }

            if (requested && info.ip.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Публичный IP", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(info.ip, style = TechnicalTypography.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize, lineHeight = MaterialTheme.typography.headlineSmall.lineHeight, fontWeight = FontWeight.Bold), color = Primary)
                        HorizontalDivider(color = Divider)
                        InfoRow("Провайдер", info.isp.ifBlank { info.org.ifBlank { "—" } })
                        InfoRow("Страна", info.country.ifBlank { "—" })
                        InfoRow("Регион", info.region.ifBlank { "—" })
                        InfoRow("Город", info.city.ifBlank { "—" })
                    }
                }
            }

            Text(
                "Определение выполняется через внешний HTTPS API. Для проверки скорости используйте отдельный speedtest.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}
