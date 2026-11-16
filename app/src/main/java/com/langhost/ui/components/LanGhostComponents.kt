package com.langhost.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.langhost.ui.theme.AccentCyan
import com.langhost.ui.theme.AmbientBlue
import com.langhost.ui.theme.Border
import com.langhost.ui.theme.Danger
import com.langhost.ui.theme.Info
import com.langhost.ui.theme.Primary
import com.langhost.ui.theme.Success
import com.langhost.ui.theme.Surface as PanelColor
import com.langhost.ui.theme.SurfaceElevated
import com.langhost.ui.theme.SurfaceHighlight
import com.langhost.ui.theme.TextPrimary
import com.langhost.ui.theme.TextSecondary
import com.langhost.ui.theme.TextTertiary
import com.langhost.ui.theme.Warning

enum class StatusTone { Neutral, Info, Success, Warning, Danger }

@Composable
fun PageHeading(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (!eyebrow.isNullOrBlank()) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = AccentCyan
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = Primary)
            }
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    tone: StatusTone = StatusTone.Neutral,
    modifier: Modifier = Modifier
) {
    val tint = when (tone) {
        StatusTone.Neutral -> TextSecondary
        StatusTone.Info -> Info
        StatusTone.Success -> Success
        StatusTone.Warning -> Warning
        StatusTone.Danger -> Danger
    }
    Row(
        modifier = modifier
            .background(tint.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(tint, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    detail: String? = null,
    accent: Color = Primary,
    modifier: Modifier = Modifier
) {
    MaterialSurface(
        modifier = modifier,
        color = PanelColor,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Border.copy(alpha = 0.78f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun ContentPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    MaterialSurface(
        modifier = modifier.fillMaxWidth(),
        color = PanelColor,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Border.copy(alpha = 0.72f))
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MaterialSurface(
            color = SurfaceElevated,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Border.copy(alpha = 0.7f))
        ) {
            Box(
                modifier = Modifier.padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = Primary)
            }
        }
    }
}

/** Abstract, evidence-neutral network signature used as a brand graphic. */
@Composable
fun NetworkOrbit(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val core = if (isConnected) AccentCyan else TextTertiary
    val node = if (isConnected) Primary else TextTertiary
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val unit = size.minDimension
        val outer = unit * 0.43f
        val points = listOf(
            Offset(c.x, c.y - outer),
            Offset(c.x + outer * 0.82f, c.y - outer * 0.18f),
            Offset(c.x + outer * 0.62f, c.y + outer * 0.76f),
            Offset(c.x - outer * 0.62f, c.y + outer * 0.76f),
            Offset(c.x - outer * 0.82f, c.y - outer * 0.18f)
        )
        drawCircle(AmbientBlue.copy(alpha = 0.32f), radius = outer * 1.2f, center = c)
        drawCircle(node.copy(alpha = 0.14f), radius = outer, center = c, style = Stroke(width = 1.dp.toPx()))
        points.forEach { point ->
            drawLine(
                color = node.copy(alpha = 0.42f),
                start = c,
                end = point,
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        drawCircle(core.copy(alpha = 0.22f), radius = outer * 0.48f, center = c)
        drawCircle(core, radius = outer * 0.22f, center = c)
        points.forEachIndexed { index, point ->
            drawCircle(
                color = if (index % 2 == 0) AccentCyan else node,
                radius = unit * if (index == 0) 0.045f else 0.035f,
                center = point
            )
            drawCircle(
                color = SurfaceHighlight,
                radius = unit * 0.075f,
                center = point,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
