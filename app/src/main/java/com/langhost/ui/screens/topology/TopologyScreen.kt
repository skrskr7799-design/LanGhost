package com.langhost.ui.screens.topology

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.langhost.data.NetworkRepository
import com.langhost.data.model.Device
import com.langhost.data.model.DeviceType
import com.langhost.ui.components.ContentPanel
import com.langhost.ui.theme.GridLine
import com.langhost.ui.components.EmptyState
import com.langhost.ui.components.PageHeading
import com.langhost.ui.components.SectionHeading
import com.langhost.ui.components.StatusBadge
import com.langhost.ui.components.StatusTone
import com.langhost.ui.theme.Background
import com.langhost.ui.theme.Border
import com.langhost.ui.theme.LanGhostShapes
import com.langhost.ui.theme.Primary
import com.langhost.ui.theme.Success
import com.langhost.ui.theme.Surface
import com.langhost.ui.theme.SurfaceElevated
import com.langhost.ui.theme.TextPrimary
import com.langhost.ui.theme.TextSecondary
import com.langhost.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private enum class TopologyPresentation(val label: String) {
    GRAPH("Схема"),
    LIST("Список")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(
    onBack: () -> Unit,
    onDeviceClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { NetworkRepository.getInstance(context) }
    val devices by repository.devices.collectAsState()
    val network by repository.networkInfo.collectAsState()
    val isScanning by repository.isScanning.collectAsState()
    val coverage by repository.scanCoverage.collectAsState()
    val lastScanTime by repository.lastScanTime.collectAsState()
    val scope = rememberCoroutineScope()

    val gateway = devices.firstOrNull { it.isGateway }
    val discoveredDevices = devices.filterNot { it.isGateway }
        .sortedWith(compareByDescending<Device> { it.isOnline }.thenBy { it.displayName.lowercase() })
    var presentation = remember { androidx.compose.runtime.mutableStateOf(TopologyPresentation.GRAPH) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Топология", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            network.ssid.takeIf { it.isNotBlank() && it != "—" } ?: "СЕТЕВОЕ ОКРУЖЕНИЕ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { repository.scanDevices() } }, enabled = !isScanning) {
                        if (isScanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        else Icon(Icons.Default.Radar, contentDescription = "Сканировать сеть", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 1120.dp).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                PageHeading(
                    title = "Карта локальной сети",
                    subtitle = "Обнаруженные узлы и их логическая группировка",
                    eyebrow = "NETWORK TOPOLOGY"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    TopologyMetric(
                        value = devices.size.toString(),
                        label = "Обнаружено",
                        modifier = Modifier.weight(1f)
                    )
                    TopologyMetric(
                        value = devices.count { it.isOnline }.toString(),
                        label = "Онлайн",
                        accent = Success,
                        modifier = Modifier.weight(1f)
                    )
                    TopologyMetric(
                        value = if (gateway == null) "—" else "1",
                        label = "Шлюз",
                        accent = Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                ContentPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Достоверность схемы", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            StatusBadge(
                                if (gateway == null) "Шлюз не найден" else "Узлы обнаружены",
                                if (gateway == null) StatusTone.Warning else StatusTone.Success
                            )
                        }
                        Text(
                            "LanGhost показывает обнаруженные устройства и их принадлежность к локальной сети. " +
                                "Физические подключения и связи между клиентами этим сканированием не подтверждаются.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (lastScanTime > 0L) {
                            Text(
                                "Последнее сканирование: ${java.text.SimpleDateFormat("HH:mm · dd.MM", java.util.Locale.getDefault()).format(java.util.Date(lastScanTime))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        } else {
                            Text("Данные появятся после первого сканирования.", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        }
                        if (coverage.truncated) {
                            StatusBadge("Обнаружение ограничено лимитом сканирования", StatusTone.Warning)
                        }
                        if (isScanning) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Primary)
                                Text("Обновляем карту сети…", style = MaterialTheme.typography.labelMedium, color = Primary)
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    SectionHeading(title = "Представление")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TopologyPresentation.entries.forEach { option ->
                            FilterChip(
                                selected = presentation.value == option,
                                onClick = { presentation.value = option },
                                label = { Text(option.label) },
                                leadingIcon = {
                                    Icon(
                                        if (option == TopologyPresentation.GRAPH) Icons.Default.AccountTree else Icons.Default.List,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (devices.isEmpty()) {
                item {
                    EmptyState(
                        title = if (isScanning) "Исследуем сеть" else "Карта пока пуста",
                        description = if (isScanning) {
                            "Обнаруженные устройства появятся здесь по мере завершения сканирования."
                        } else {
                            "Запустите сканирование, чтобы LanGhost обнаружил доступные устройства локальной сети."
                        },
                        icon = Icons.Default.Radar,
                        actionLabel = if (isScanning) null else "Начать сканирование",
                        onAction = if (isScanning) null else ({ scope.launch { repository.scanDevices() } })
                    )
                }
            } else if (presentation.value == TopologyPresentation.GRAPH) {
                item {
                    TopologyGraph(
                        gateway = gateway,
                        devices = discoveredDevices.take(24),
                        onDeviceClick = onDeviceClick
                    )
                }
                if (discoveredDevices.size > 24) {
                    item {
                        Text(
                            "На схеме показаны первые 24 устройства. Полный список доступен в режиме «Список».",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                item {
                    SectionHeading(
                        title = "Обнаруженные устройства",
                        actionLabel = "${devices.size} всего"
                    )
                }
                if (gateway != null) {
                    item {
                        TopologyDeviceRow(device = gateway, isGateway = true, onClick = { onDeviceClick(gateway.ip) })
                    }
                }
                items(discoveredDevices, key = { it.stableId }) { device ->
                    TopologyDeviceRow(device = device, isGateway = false, onClick = { onDeviceClick(device.ip) })
                }
            }
        }
        }
    }
}

@Composable
private fun TopologyMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = TextPrimary
) {
    ContentPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun TopologyGraph(
    gateway: Device?,
    devices: List<Device>,
    onDeviceClick: (String) -> Unit
) {
    val nodes = remember(gateway, devices) { listOfNotNull(gateway) + devices }

    ContentPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Логическая карта", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text("${nodes.size} узлов", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .pointerInput(nodes) {
                        detectTapGestures { tap ->
                            val positions = topologyPositions(nodes, size)
                            val nearest = positions.minByOrNull { (_, position) -> (position - tap).getDistance() }
                            if (nearest != null && (nearest.value - tap).getDistance() <= 48f) {
                                nodes.firstOrNull { it.stableId == nearest.key }?.let { onDeviceClick(it.ip) }
                            }
                        }
                    }
            ) {
                var gridX = 0f
                val gridStep = 34.dp.toPx()
                while (gridX < size.width) {
                    drawLine(GridLine.copy(alpha = 0.55f), Offset(gridX, 0f), Offset(gridX, size.height), strokeWidth = 1.dp.toPx())
                    gridX += gridStep
                }
                var gridY = 0f
                while (gridY < size.height) {
                    drawLine(GridLine.copy(alpha = 0.55f), Offset(0f, gridY), Offset(size.width, gridY), strokeWidth = 1.dp.toPx())
                    gridY += gridStep
                }
                val positions = topologyPositions(nodes, IntSize(size.width.toInt(), size.height.toInt()))
                val gatewayPosition = gateway?.let { positions[it.stableId] }

                if (gatewayPosition != null) {
                    devices.forEach { device ->
                        val destination = positions[device.stableId] ?: return@forEach
                        drawLine(
                            color = Primary.copy(alpha = 0.42f),
                            start = gatewayPosition,
                            end = destination,
                            strokeWidth = 1.6f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
                        )
                    }
                }

                nodes.forEach { device ->
                    val position = positions[device.stableId] ?: return@forEach
                    val isGateway = device.isGateway
                    val nodeColor = when {
                        isGateway -> Primary
                        device.isOnline -> Success
                        else -> TextTertiary
                    }
                    drawCircle(nodeColor.copy(alpha = 0.14f), radius = if (isGateway) 35f else 27f, center = position)
                    drawCircle(nodeColor, radius = if (isGateway) 17f else 12f, center = position)
                    drawCircle(Color.White.copy(alpha = 0.72f), radius = if (isGateway) 17f else 12f, center = position, style = Stroke(width = 1.5f))

                    val paint = AndroidPaint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.rgb(225, 233, 245)
                        textSize = 11.dp.toPx()
                        textAlign = AndroidPaint.Align.CENTER
                        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    }
                    val label = if (isGateway) "Шлюз" else device.displayName.take(15)
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        position.x,
                        position.y + if (isGateway) 48f else 39f,
                        paint
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(Primary, "Шлюз")
                LegendDot(Success, "Онлайн")
                LegendDot(TextTertiary, "Оффлайн")
            }
            Text(
                if (gateway == null) {
                    "Шлюз не обнаружен. Узлы показаны без предположений о связях между ними."
                } else {
                    "Пунктир обозначает логическую группировку через шлюз, а не подтверждённый физический кабель или Wi-Fi-линк."
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

private fun topologyPositions(nodes: List<Device>, canvasSize: IntSize): Map<String, Offset> {
    if (nodes.isEmpty()) return emptyMap()
    val gateway = nodes.firstOrNull { it.isGateway }
    val clients = nodes.filterNot { it.isGateway }
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val radius = min(canvasSize.width, canvasSize.height) * 0.34f
    return buildMap {
        gateway?.let { put(it.stableId, center) }
        clients.forEachIndexed { index, device ->
            val angle = (2.0 * PI * index / clients.size.coerceAtLeast(1)) - PI / 2.0
            val point = Offset(
                center.x + radius * cos(angle).toFloat(),
                center.y + radius * sin(angle).toFloat()
            )
            put(device.stableId, point)
        }
    }
}

@Composable
private fun TopologyDeviceRow(device: Device, isGateway: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        contentColor = TextPrimary,
        shape = LanGhostShapes.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(SurfaceElevated, LanGhostShapes.control),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isGateway) Icons.Default.AccountTree else Icons.Default.Devices,
                    contentDescription = null,
                    tint = if (isGateway) Primary else TextSecondary
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.displayName, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${device.ip} · ${typeLabel(device.deviceType)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            StatusBadge(
                if (isGateway) "Шлюз" else if (device.isOnline) "Онлайн" else "Оффлайн",
                when {
                    isGateway -> StatusTone.Info
                    device.isOnline -> StatusTone.Success
                    else -> StatusTone.Neutral
                }
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

private fun typeLabel(type: DeviceType): String = when (type) {
    DeviceType.PHONE -> "Телефон"
    DeviceType.TABLET -> "Планшет"
    DeviceType.TV -> "ТВ"
    DeviceType.PC -> "ПК"
    DeviceType.LAPTOP -> "Ноутбук"
    DeviceType.ROUTER -> "Роутер"
    DeviceType.PRINTER -> "Принтер"
    DeviceType.NAS -> "NAS"
    DeviceType.CAMERA -> "Камера"
    DeviceType.IOT -> "IoT"
    DeviceType.SERVER -> "Сервер"
    DeviceType.UNKNOWN -> "Устройство"
}
