package com.langhost.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.data.AppSettings
import com.langhost.data.AppSettingsData
import com.langhost.data.DeviceProbe
import com.langhost.data.PrivacyFormatter
import com.langhost.ui.components.ContentPanel
import com.langhost.ui.components.PageHeading
import com.langhost.ui.components.StatusBadge
import com.langhost.ui.components.StatusTone
import com.langhost.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UtilitiesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appSettings by remember(context) { AppSettings(context.applicationContext).settingsFlow }.collectAsState(initial = AppSettingsData())
    var host by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var activeOperation by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun execute(operation: String) {
        if (loading || host.isBlank()) return
        scope.launch {
            loading = true
            activeOperation = operation
            result = ""
            failed = false
            try {
                result = if (operation == "PING") {
                    runPing(host.trim(), appSettings.maskNetworkIdentifiers)
                } else {
                    runDns(host.trim(), appSettings.maskNetworkIdentifiers)
                }
                failed = result.startsWith("Не удалось") || result.startsWith("Недоступен") || result.startsWith("Время ожидания")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                result = "Операция завершилась ошибкой. Проверьте адрес и повторите запрос."
                failed = true
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Диагностика узла", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 840.dp
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 28.dp else 18.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                PageHeading(
                    title = "Ping и DNS",
                    subtitle = "Проверьте доступность узла и узнайте, какие адреса возвращает DNS",
                    eyebrow = "NETWORK DIAGNOSTICS"
                )
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                        ProbePanel(host, { host = it }, loading, { execute("PING") }, { execute("DNS") }, Modifier.weight(0.95f))
                        ResultPanel(result, loading, activeOperation, failed, Modifier.weight(1.05f))
                    }
                } else {
                    ProbePanel(host, { host = it }, loading, { execute("PING") }, { execute("DNS") })
                    ResultPanel(result, loading, activeOperation, failed)
                }
                Text(
                    "Ping использует TCP-проверку портов 80/443, а не ICMP. DNS Lookup показывает результаты системного разрешения имени.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ProbePanel(
    host: String,
    onHostChange: (String) -> Unit,
    loading: Boolean,
    onPing: () -> Unit,
    onDns: () -> Unit,
    modifier: Modifier = Modifier
) {
    ContentPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusBadge("LOCAL PROBE", StatusTone.Info)
            Text("Целевой узел", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("Введите IP-адрес или доменное имя. Запрос выполняется только после выбора операции.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("IP или домен") },
                placeholder = { Text("например, 192.168.1.1") },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = LanGhostShapes.control,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Border,
                    focusedLabelColor = Primary,
                    cursorColor = Primary
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onPing,
                    enabled = host.isNotBlank() && !loading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Default.NetworkPing, contentDescription = null)
                    Text("  Ping", maxLines = 1)
                }
                Button(
                    onClick = onDns,
                    enabled = host.isNotBlank() && !loading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = TextPrimary)
                ) {
                    Icon(Icons.Default.Dns, contentDescription = null)
                    Text("  DNS", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ResultPanel(
    result: String,
    loading: Boolean,
    activeOperation: String,
    failed: Boolean,
    modifier: Modifier = Modifier
) {
    ContentPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Результат проверки", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            when {
                loading -> {
                    StatusBadge(if (activeOperation == "PING") "PING IN PROGRESS" else "DNS LOOKUP", StatusTone.Info)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                    Text("Ожидаем ответ узла…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                result.isBlank() -> {
                    StatusBadge("AWAITING INPUT", StatusTone.Neutral)
                    Text("Здесь появится результат. Выберите Ping или DNS после ввода адреса.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                else -> {
                    StatusBadge(if (failed) "CHECK NEEDS ATTENTION" else "RESPONSE RECEIVED", if (failed) StatusTone.Warning else StatusTone.Success)
                    Text(result, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        }
    }
}

private suspend fun resolveHost(host: String): Array<InetAddress>? =
    withTimeoutOrNull(4_000L) {
        runInterruptible(Dispatchers.IO) { InetAddress.getAllByName(host) }
    }

private fun isValidHostInput(host: String): Boolean =
    host.isNotBlank() &&
        host.length <= 253 &&
        !host.any(Char::isWhitespace) &&
        host.none { it == '/' || it == '\\' }

private suspend fun runPing(host: String, maskIdentifiers: Boolean): String {
    if (!isValidHostInput(host)) return "Укажите корректный IP или доменное имя"
    val addresses = try {
        resolveHost(host) ?: return "Время ожидания DNS истекло"
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        return "Не удалось определить адрес хоста"
    }
    val address = addresses.firstOrNull()?.hostAddress ?: return "DNS не вернул адресов"
    val ms = DeviceProbe.tcpPing(address, 80) ?: DeviceProbe.tcpPing(address, 443)
    return if (ms != null) "TCP reachable ≈ ${ms} ms · ${PrivacyFormatter.ip(address, maskIdentifiers)}" else "Недоступен (TCP 80/443)"
}

private suspend fun runDns(host: String, maskIdentifiers: Boolean): String {
    if (!isValidHostInput(host)) return "Укажите корректный IP или доменное имя"
    return try {
        val all = resolveHost(host) ?: return "Время ожидания DNS истекло"
        if (all.isEmpty()) return "DNS не вернул адресов"
        buildString {
            appendLine("DNS: ${maskHostIfAddress(host, maskIdentifiers)}")
            all.take(16).forEach { appendLine(PrivacyFormatter.ip(it.hostAddress ?: "", maskIdentifiers)) }
            if (all.size > 16) appendLine("Показаны первые 16 адресов")
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        "Не удалось выполнить DNS-запрос"
    }
}

private fun maskHostIfAddress(host: String, enabled: Boolean): String =
    if (host.count { it == '.' } == 3 || ':' in host) PrivacyFormatter.ip(host, enabled) else host
