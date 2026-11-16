package com.langhost.ui.screens.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.data.DeviceProbe
import com.langhost.data.PingHistoryStore
import com.langhost.data.PingSample
import com.langhost.ui.theme.*
import com.langhost.ui.components.LoadingState
import com.langhost.ui.components.ErrorState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PingHistorySection(ip: String) {
    val context = LocalContext.current
    val store = remember { PingHistoryStore(context) }
    val samples by store.historyFlow(ip).collectAsState(initial = emptyList())
    val stats = remember(samples) { store.computeStats(samples) }
    val scope = rememberCoroutineScope()
    var probing by remember { mutableStateOf(false) }
    var probeError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("История TCP-отклика", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Button(
                    onClick = {
                        scope.launch {
                            probing = true
                            probeError = null
                            try {
                                val ms = DeviceProbe.tcpPing(ip, 80) ?: DeviceProbe.tcpPing(ip, 443) ?: DeviceProbe.tcpPing(ip, 22)
                                store.addSample(ip, ms)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                probeError = "Не удалось сохранить результат проверки. Предыдущая история сохранена."
                            } finally {
                                probing = false
                            }
                        }
                    },
                    enabled = !probing,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(if (probing) "Проверка..." else "Проверить")
                }
            }

            probeError?.let { ErrorState(title = "Ошибка истории TCP", description = it, onRetry = { scope.launch { probing = true; try { val ms = DeviceProbe.tcpPing(ip, 80) ?: DeviceProbe.tcpPing(ip, 443) ?: DeviceProbe.tcpPing(ip, 22); store.addSample(ip, ms); probeError = null } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { probeError = "Не удалось сохранить результат проверки. Предыдущая история сохранена." } finally { probing = false } } }) }
            if (probing) LoadingState(title = "Проверяем TCP-отклик", description = "Пробуем TCP-подключение к портам 80, 443 и 22; это не ICMP ping.")
            if (samples.isEmpty()) {
                Text("Запустите проверку TCP-отклика, чтобы начать историю", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            } else {
                Text(
                    if (samples.any { it.latencyMs != null }) "Среднее ${stats.avgMs.toInt()} ms · минимум ${stats.minMs} ms · максимум ${stats.maxMs} ms · неуспешные TCP-пробы ${stats.lossPercent.toInt()}%" else "Нет успешных TCP-подключений · неуспешные TCP-пробы 100%",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (samples.all { it.latencyMs == null }) Text("TCP-подключение к портам 80, 443 и 22 не установлено. Это не доказывает недоступность устройства: службы могут использовать другие порты или блокировать подключения.", style = MaterialTheme.typography.bodySmall, color = Warning)
                val okSamples = samples.mapNotNull { it.latencyMs?.toFloat() }
                if (okSamples.size >= 2) {
                    val maxY = (okSamples.maxOrNull() ?: 1f).coerceAtLeast(1f)
                    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                        val path = Path()
                        okSamples.forEachIndexed { i, v ->
                            val x = size.width * i / (okSamples.size - 1).coerceAtLeast(1)
                            val y = size.height * (1f - v / maxY)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = Primary, style = Stroke(width = 3f))
                    }
                }
            }
        }
    }
}
