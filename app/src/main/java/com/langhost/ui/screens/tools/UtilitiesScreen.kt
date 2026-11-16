package com.langhost.ui.screens.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.data.DeviceProbe
import com.langhost.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilitiesScreen(onBack: () -> Unit) {
    var host by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Утилиты", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("IP или домен") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = LanGhostShapes.control,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Border,
                    focusedLabelColor = Primary,
                    cursorColor = Primary
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            result = runPing(host.trim())
                            loading = false
                        }
                    },
                    enabled = host.isNotBlank() && !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("Ping (TCP)") }

                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            result = runDns(host.trim())
                            loading = false
                        }
                    },
                    enabled = host.isNotBlank() && !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("DNS Lookup") }
            }

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
            }

            if (result.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.72f))
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
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

private suspend fun runPing(host: String): String {
    if (!isValidHostInput(host)) return "Укажите корректный IP или доменное имя"
    val addresses = try {
        resolveHost(host) ?: return "Время ожидания DNS истекло"
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        return "Не удалось определить адрес хоста"
    }
    val address = addresses.firstOrNull()?.hostAddress
        ?: return "DNS не вернул адресов"
    val ms = DeviceProbe.tcpPing(address, 80) ?: DeviceProbe.tcpPing(address, 443)
    return if (ms != null) "TCP reachable ≈ ${ms} ms" else "Недоступен (TCP 80/443)"
}

private suspend fun runDns(host: String): String {
    if (!isValidHostInput(host)) return "Укажите корректный IP или доменное имя"
    return try {
        val all = resolveHost(host) ?: return "Время ожидания DNS истекло"
        if (all.isEmpty()) return "DNS не вернул адресов"
        buildString {
            appendLine("DNS: $host")
            all.take(16).forEach { appendLine(it.hostAddress) }
            if (all.size > 16) appendLine("Показаны первые 16 адресов")
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        "Не удалось выполнить DNS-запрос"
    }
}
