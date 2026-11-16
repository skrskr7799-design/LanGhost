package com.langhost.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langhost.ui.theme.AccentCyan
import com.langhost.ui.theme.Border
import com.langhost.ui.theme.Danger
import com.langhost.ui.theme.Info
import com.langhost.ui.theme.Surface as PanelColor
import com.langhost.ui.theme.TextPrimary
import com.langhost.ui.theme.TextSecondary
import com.langhost.ui.theme.Warning

/**
 * Shared operation-state primitives. Keep status copy factual: a failed or incomplete
 * operation must never be presented as a successful empty result.
 */
@Composable
fun LoadingState(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    StateSurface(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = AccentCyan,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (!description.isNullOrBlank()) {
                    Text(description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun ErrorState(
    title: String,
    description: String,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Повторить",
    modifier: Modifier = Modifier
) {
    StateSurface(modifier = modifier, accent = Danger) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Danger,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (onRetry != null) {
                    TextButton(onClick = onRetry) {
                        Text(retryLabel, color = Danger)
                    }
                }
            }
        }
    }
}

enum class DataNoticeTone { Info, Warning }

@Composable
fun DataQualityNotice(
    title: String,
    description: String,
    tone: DataNoticeTone = DataNoticeTone.Info,
    modifier: Modifier = Modifier
) {
    val tint = when (tone) {
        DataNoticeTone.Info -> Info
        DataNoticeTone.Warning -> Warning
    }
    val icon: ImageVector = when (tone) {
        DataNoticeTone.Info -> Icons.Default.Info
        DataNoticeTone.Warning -> Icons.Default.Warning
    }
    StateSurface(modifier = modifier, accent = tint) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StateSurface(
    modifier: Modifier = Modifier,
    accent: Color = AccentCyan,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PanelColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

