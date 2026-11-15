package com.langhost.ui.screens.topology

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.langhost.data.NetworkRepository
import com.langhost.data.model.Device
import com.langhost.data.model.DeviceType
import com.langhost.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(
    onBack: () -> Unit,
    onDeviceClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val repo = remember { NetworkRepository.getInstance(context) }
    val devices by repo.devices.collectAsState()
    val network by repo.networkInfo.collectAsState()

    val gateway = devices.find { it.isGateway }
    val others = devices.filter { !it.isGateway }.sortedByDescending { it.isOnline }

    var selected by remember { mutableStateOf<Device?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Топология", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            network.ssid.ifBlank { "Сеть" },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Сначала выполните сканирование", color = TextSecondary)
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    TopologyCanvas(
                        gateway = gateway,
                        devices = others,
                        onNodeClick = { selected = it; onDeviceClick(it.ip) }
                    )
                }

                selected?.let { d ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(if (d.isOnline) Success else TextTertiary, CircleShape)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(d.displayName, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text("${d.ip} · ${typeLabel(d.deviceType)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }

                // Legend
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendDot(Success, "Онлайн")
                    LegendDot(TextTertiary, "Оффлайн")
                    LegendDot(Primary, "Шлюз")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun TopologyCanvas(
    gateway: Device?,
    devices: List<Device>,
    onNodeClick: (Device) -> Unit
) {
    val nodes = remember(gateway, devices) {
        buildList {
            gateway?.let { add(it) }
            addAll(devices.take(24))
        }
    }

    var positions by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(nodes) {
                detectTapGestures { tap ->
                    val hit = positions.entries.minByOrNull { (_, pos) ->
                        (pos - tap).getDistance()
                    }
                    if (hit != null && (hit.value - tap).getDistance() < 48f) {
                        nodes.find { it.stableId == hit.key || it.ip == hit.key }?.let(onNodeClick)
                    }
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(size.width, size.height) * 0.32f

        val posMap = mutableMapOf<String, Offset>()
        val gwPos = Offset(cx, cy)
        gateway?.let { posMap[it.stableId] = gwPos }

        val ring = devices.take(24)
        ring.forEachIndexed { index, d ->
            val angle = (2 * PI * index / ring.size.coerceAtLeast(1)) - PI / 2
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            posMap[d.stableId] = Offset(x, y)
        }
        positions = posMap

        // Edges from gateway
        val lineColor = Color(0xFF3A3A4A)
        ring.forEach { d ->
            val to = posMap[d.stableId] ?: return@forEach
            drawLine(
                color = lineColor,
                start = gwPos,
                end = to,
                strokeWidth = 2f
            )
        }

        // Gateway node
        gateway?.let {
            drawCircle(color = Primary.copy(alpha = 0.25f), radius = 36f, center = gwPos)
            drawCircle(color = Primary, radius = 18f, center = gwPos)
            drawCircle(color = Color.White, radius = 18f, center = gwPos, style = Stroke(2f))
        }

        // Device nodes
        ring.forEach { d ->
            val pos = posMap[d.stableId] ?: return@forEach
            val color = when {
                !d.isOnline -> TextTertiary
                d.isLocalDevice -> Secondary
                else -> Success
            }
            drawCircle(color = color.copy(alpha = 0.3f), radius = 28f, center = pos)
            drawCircle(color = color, radius = 12f, center = pos)
        }
    }

    // Labels overlay (simplified — gateway label center)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (gateway != null) {
            Text(
                text = gateway.displayName.take(12),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = 28.dp)
            )
        }
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
