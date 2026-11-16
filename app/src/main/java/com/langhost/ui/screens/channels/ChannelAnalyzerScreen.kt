package com.langhost.ui.screens.channels

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.langhost.data.ChannelAnalyzer
import com.langhost.data.ChannelLoad
import com.langhost.data.NetworkRepository
import com.langhost.data.model.NetworkInfo
import com.langhost.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SpectrumBand(val title: String) {
    GHZ_24("2.4 ГГц"), GHZ_5("5 ГГц"), GHZ_6("6 ГГц")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChannelAnalyzerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { NetworkRepository.getInstance(context) }
    val network by repo.networkInfo.collectAsState()
    val networks by repo.nearbyNetworks.collectAsState()
    val completed by repo.nearbyScanCompleted.collectAsState()
    val scanTime by repo.nearbyScanTime.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var selectedBand by remember { mutableStateOf(SpectrumBand.GHZ_24) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    val permissionState = rememberMultiplePermissionsState(permissions)
    val advice = remember(networks, network.channel) { ChannelAnalyzer.analyze(networks, network.channel) }

    fun scan() {
        scanError = null
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
            return
        }
        scope.launch {
            scanning = true
            try {
                repo.scanNearbyNetworks()
                if (repo.nearbyScanTime.value == 0L) scanError = "Android не обновил данные. Текущая оценка может быть устаревшей."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                scanError = "Нет разрешения на сканирование Wi-Fi."
            } catch (_: Exception) {
                scanError = "Не удалось обновить радиоэфир. Попробуйте ещё раз."
            } finally {
                scanning = false
            }
        }
    }

    val loads = when (selectedBand) {
        SpectrumBand.GHZ_24 -> advice.band24
        SpectrumBand.GHZ_5 -> advice.band5
        SpectrumBand.GHZ_6 -> advice.band6
    }
    val best = when (selectedBand) {
        SpectrumBand.GHZ_24 -> advice.best24
        SpectrumBand.GHZ_5 -> advice.best5
        SpectrumBand.GHZ_6 -> advice.best6
    }
    val visibleLoads = loads.filter { it.networkCount > 0 || it.channel == best }.sortedBy { it.channel }
    val lastScan = if (scanTime > 0L) SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(scanTime)) else "нет данных"
    val fresh = scanTime > 0L && System.currentTimeMillis() >= scanTime && System.currentTimeMillis() - scanTime <= 5 * 60 * 1000L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Анализ каналов", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextPrimary) } },
                actions = {
                    IconButton(onClick = { scan() }, enabled = !scanning) {
                        if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        else Icon(Icons.Default.Radar, "Сканировать", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = TextPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SpectrumIntro(network, networks.size, fresh, completed, scanning, lastScan, scanError, ::scan, !permissionState.allPermissionsGranted, { permissionState.launchMultiplePermissionRequest() })
                        BandSelector(selectedBand, { selectedBand = it })
                        SpectrumPanel(loads, best, network.channel, selectedBand)
                        RecommendationPanel(advice.recommendation, best, selectedBand, networks.size, completed)
                    }
                    Column(Modifier.weight(0.85f).fillMaxHeight()) {
                        ChannelInventory(visibleLoads, best, network.channel, selectedBand, Modifier.fillMaxSize())
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SpectrumIntro(network, networks.size, fresh, completed, scanning, lastScan, scanError, ::scan, !permissionState.allPermissionsGranted, { permissionState.launchMultiplePermissionRequest() })
                    BandSelector(selectedBand, { selectedBand = it })
                    SpectrumPanel(loads, best, network.channel, selectedBand)
                    RecommendationPanel(advice.recommendation, best, selectedBand, networks.size, completed)
                    ChannelInventory(visibleLoads, best, network.channel, selectedBand)
                }
            }
        }
    }
}

@Composable
private fun SpectrumIntro(
    network: NetworkInfo, total: Int, fresh: Boolean, completed: Boolean, scanning: Boolean,
    lastScan: String, error: String?, onScan: () -> Unit, needsPermission: Boolean, onPermission: () -> Unit
) {
    Surface(color = Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Border.copy(alpha = 0.8f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("RADIO INTELLIGENCE / CHANNEL MAP", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                    Text("Загрузка радиоэфира", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.GraphicEq, null, tint = AccentCyan, modifier = Modifier.size(30.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column {
                    Text(total.toString(), style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("точек доступа", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
                Column {
                    Text(if (network.channel > 0) network.channel.toString() else "—", style = MaterialTheme.typography.headlineMedium, color = Primary, fontWeight = FontWeight.Bold)
                    Text("текущий канал", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
                Column {
                    Text(if (fresh) "LIVE" else if (completed) "STALE" else "—", style = MaterialTheme.typography.titleLarge, color = if (fresh) Success else Warning, fontWeight = FontWeight.Bold)
                    Text(lastScan, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
            }
            Text(
                when { scanning -> "Сканирование выполняется…"; fresh -> "Данные актуальны. Оценка основана на обнаруженных точках доступа."; completed -> "Показаны последние доступные результаты. Обнови сканирование перед изменением настроек роутера."; else -> "До первого сканирования пустые каналы не считаются свободными." },
                style = MaterialTheme.typography.bodySmall, color = TextSecondary
            )
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
            if (needsPermission) {
                Button(onClick = onPermission, modifier = Modifier.fillMaxWidth()) { Text("Предоставить разрешения") }
            } else if (!completed || !fresh) {
                OutlinedButton(onClick = onScan, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Radar, null); Spacer(Modifier.width(8.dp)); Text(if (completed) "Обновить анализ" else "Сканировать радиоэфир")
                }
            }
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = Warning)
        }
    }
}

@Composable
private fun BandSelector(selected: SpectrumBand, onSelect: (SpectrumBand) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpectrumBand.entries.forEach { band ->
            FilterChip(
                selected = selected == band, onClick = { onSelect(band) }, label = { Text(band.title) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.18f), selectedLabelColor = Primary, labelColor = TextSecondary)
            )
        }
    }
}

@Composable
private fun SpectrumPanel(loads: List<ChannelLoad>, best: Int?, current: Int, band: SpectrumBand) {
    Surface(color = Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Border.copy(alpha = 0.8f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("SPECTRUM VIEW", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                    Text("Карта каналов · ${band.title}", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                if (best != null) StatusPill("РЕКОМЕНДУЕМЫЙ $best", Success)
            }
            if (loads.isEmpty()) {
                Text("Нет данных для выбранного диапазона.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            } else {
                ChannelSpectrumChart(loads.sortedBy { it.channel }, best, current, Modifier.fillMaxWidth().height(142.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(Success, "Свободнее")
                    LegendDot(Primary, "Текущий")
                    LegendDot(Danger, "Загружен")
                }
                Text("Высота столбца показывает относительную оценку пересечения с обнаруженными сетями, а не измерение реальных помех.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
        }
    }
}

@Composable
private fun ChannelSpectrumChart(loads: List<ChannelLoad>, best: Int?, current: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val maxScore = (loads.maxOfOrNull { it.score } ?: 1).coerceAtLeast(1).toFloat()
        val slot = size.width / loads.size.coerceAtLeast(1)
        val barWidth = (slot * 0.58f).coerceAtLeast(2.dp.toPx())
        for (line in 0..3) {
            val y = size.height * line / 3f
            drawLine(GridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        loads.forEachIndexed { index, load ->
            val height = (load.score / maxScore * (size.height - 12.dp.toPx())).coerceAtLeast(4.dp.toPx())
            val x = slot * index + (slot - barWidth) / 2f
            val color = when {
                load.channel == best -> Success
                load.channel == current -> Primary
                load.networkCount >= 3 -> Danger
                load.networkCount > 0 -> Warning
                else -> SurfaceHighlight
            }
            drawRoundRect(
                color = color.copy(alpha = if (load.networkCount == 0) 0.55f else 0.92f),
                topLeft = Offset(x, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
private fun RecommendationPanel(recommendation: String, best: Int?, band: SpectrumBand, total: Int, completed: Boolean) {
    Surface(color = SurfaceHighlight.copy(alpha = 0.38f), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Border.copy(alpha = 0.75f))) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ОЦЕНКА / NEXT ACTION", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.SemiBold)
            Text(if (best != null && completed) "Кандидат для настройки" else "Нужно больше данных", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            if (best != null && completed) {
                Text("Канал $best · ${band.title}", style = MaterialTheme.typography.headlineSmall, color = Success, fontWeight = FontWeight.Bold)
            }
            Text(
                if (!completed || total == 0) "Сначала выполните сканирование. Отсутствие результатов не означает, что эфир свободен."
                else recommendation,
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
            Text("Это оценка по видимым точкам доступа. Она не проверяет поддержку канала роутером, региональные ограничения, ширину канала и скрытые источники помех.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
    }
}

@Composable
private fun ChannelInventory(loads: List<ChannelLoad>, best: Int?, current: Int, band: SpectrumBand, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Каналы диапазона", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("${band.title} · занятые и рекомендованный", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Text("${loads.size}", style = TechnicalTypography, color = TextTertiary)
        }
        if (loads.isEmpty()) {
            Surface(color = Surface, shape = RoundedCornerShape(16.dp)) {
                Text("Пока нет данных по каналам. Выполните сканирование.", modifier = Modifier.padding(18.dp), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(loads, key = { "${band.name}-${it.channel}" }) { load -> ChannelLoadRow(load, load.channel == best, load.channel == current) }
            }
        }
    }
}

@Composable
private fun ChannelLoadRow(load: ChannelLoad, best: Boolean, current: Boolean) {
    Surface(
        color = when { best -> Success.copy(alpha = 0.10f); current -> Primary.copy(alpha = 0.10f); else -> Surface },
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, when { best -> Success.copy(alpha = 0.45f); current -> Primary.copy(alpha = 0.45f); else -> Border.copy(alpha = 0.7f) })
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Канал ${load.channel}", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        when { best -> "Наименьшая оценка загрузки"; current -> "Используется сейчас"; else -> "${load.networkCount} пересекающихся сетей" },
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${load.networkCount}", style = MaterialTheme.typography.titleMedium, color = if (load.networkCount == 0) Success else TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("сетей", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
            }
            LinearProgressIndicator(
                progress = { (load.score / 600f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                color = when { best -> Success; load.networkCount >= 3 -> Danger; load.networkCount > 0 -> Warning; else -> SurfaceHighlight },
                trackColor = SurfaceVariant
            )
            if (load.networkCount > 0) Text("Сильнейшая точка: ${load.strongestDbm} dBm · оценка ${load.score}", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.13f), shape = RoundedCornerShape(8.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
