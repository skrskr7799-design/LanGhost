package com.langhost.ui.screens.internet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.PrivacyFormatter
import com.langhost.data.PublicIpLookup
import com.langhost.data.PublicNetworkInfo
import com.langhost.ui.components.ContentPanel
import com.langhost.ui.components.DataNoticeTone
import com.langhost.ui.components.DataQualityNotice
import com.langhost.ui.components.ErrorState
import com.langhost.ui.components.PageHeading
import com.langhost.ui.components.StatusBadge
import com.langhost.ui.components.StatusTone
import com.langhost.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PublicIpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appSettings by remember(context) { AppSettings(context.applicationContext).settingsFlow }.collectAsState(initial = AppSettingsData())
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
                if (result.ip.isBlank()) error = "Сервис не вернул публичный IP. Проверьте подключение и повторите запрос."
                info = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Не удалось получить данные внешней сети. Попробуйте ещё раз."
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Внешняя сеть", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 840.dp
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 28.dp else 18.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                PageHeading(
                    title = "Публичный адрес",
                    subtitle = "Информация о том, как ваша сеть видна из интернета",
                    eyebrow = "EXTERNAL NETWORK"
                )
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                        RequestPanel(loading, requested, ::refresh, Modifier.weight(0.9f))
                        NetworkResultPanel(info, requested, loading, error, appSettings.maskNetworkIdentifiers, Modifier.weight(1.1f))
                    }
                } else {
                    RequestPanel(loading, requested, ::refresh)
                    NetworkResultPanel(info, requested, loading, error, appSettings.maskNetworkIdentifiers)
                }
                Text(
                    "Геолокация определяется по данным IP и может быть неточной. Это не GPS-позиция устройства.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun RequestPanel(
    loading: Boolean,
    requested: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    ContentPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
            StatusBadge("EXTERNAL LOOKUP", StatusTone.Info)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Public, contentDescription = null, tint = AccentCyan)
                Text("Определение внешней сети", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Запрос не выполняется автоматически. После подтверждения LanGhost обратится к внешнему HTTPS-сервису за публичным IP и, если доступно, сведениями о провайдере и примерном регионе.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Button(
                onClick = onRefresh,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp).height(18.dp), color = OnPrimary, strokeWidth = 2.dp)
                }
                Text(if (loading) "Определяем…" else if (requested) "Повторить запрос" else "Определить внешний IP")
            }
            Text(
                "Основной источник: ipapi.co. Резервный api.ipify.org используется только для получения IP-адреса. Данные локальной сети не отправляются.",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun NetworkResultPanel(
    info: PublicNetworkInfo,
    requested: Boolean,
    loading: Boolean,
    error: String?,
    maskIdentifiers: Boolean,
    modifier: Modifier = Modifier
) {
    ContentPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Сведения о подключении", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            when {
                loading && info.ip.isBlank() -> {
                    StatusBadge("WAITING FOR RESPONSE", StatusTone.Info)
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                    Text("Получаем ответ внешнего сервиса…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                !requested -> {
                    StatusBadge("NO REQUEST YET", StatusTone.Neutral)
                    Text("После запроса здесь появится публичный IP и доступные сведения о внешней сети.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                info.ip.isNotBlank() -> {
                    StatusBadge(if (loading) "REFRESHING" else "DATA RECEIVED", if (loading) StatusTone.Info else StatusTone.Success)
                    Text("Публичный IP", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(
                        PrivacyFormatter.ip(info.ip, maskIdentifiers),
                        style = TechnicalTypography.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize, lineHeight = MaterialTheme.typography.headlineMedium.lineHeight, fontWeight = FontWeight.Bold),
                        color = Primary
                    )
                    HorizontalDivider(color = Divider)
                    NetworkInfoRow("Провайдер", info.isp.ifBlank { info.org.ifBlank { "—" } })
                    NetworkInfoRow("Страна", info.country.ifBlank { "—" })
                    NetworkInfoRow("Регион", info.region.ifBlank { "—" })
                    NetworkInfoRow("Город", info.city.ifBlank { "—" })
                    if (error != null) {
                        ErrorState(
                            title = "Обновление не завершено",
                            description = error,
                            onRetry = { /* retry is provided by the request panel */ }
                        )
                    } else if (info.isp.isBlank() && info.org.isBlank() && info.country.isBlank() && info.region.isBlank() && info.city.isBlank()) {
                        DataQualityNotice(
                            title = "Получены только основные данные",
                            description = "Публичный IP определён, но внешняя служба не предоставила сведения о провайдере и регионе.",
                            tone = DataNoticeTone.Info
                        )
                    }
                }
                else -> {
                    StatusBadge("LOOKUP FAILED", StatusTone.Warning)
                    ErrorState(
                        title = "Не удалось получить сведения",
                        description = error ?: "Внешняя служба не вернула данные.",
                        onRetry = { /* retry is provided by the request panel */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            value,
            modifier = Modifier.weight(1f).padding(start = 18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
