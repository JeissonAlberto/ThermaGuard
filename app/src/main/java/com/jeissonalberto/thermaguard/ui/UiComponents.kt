package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.ui.theme.TGCritical
import com.jeissonalberto.thermaguard.ui.theme.TGCriticalContainer
import com.jeissonalberto.thermaguard.ui.theme.TGPrimary
import com.jeissonalberto.thermaguard.ui.theme.TGPrimaryContainer
import com.jeissonalberto.thermaguard.ui.theme.TGSuccess
import com.jeissonalberto.thermaguard.ui.theme.TGTextMuted
import com.jeissonalberto.thermaguard.ui.theme.TGWarning
import com.jeissonalberto.thermaguard.ui.theme.TGWarningContainer

@Composable
fun TGCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) { content() }
}

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TGPrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Text(title, style = MaterialTheme.typography.headlineSmall)
        description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TGTextMuted,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = TGTextMuted,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp
    )
}

@Composable
fun StatusPill(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier
) {
    val (container, content) = when (tone) {
        StatusTone.NOMINAL -> TGPrimaryContainer to TGPrimary
        StatusTone.WARNING -> TGWarningContainer to TGWarning
        StatusTone.CRITICAL -> TGCriticalContainer to TGCritical
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to TGTextMuted
    }
    Surface(
        modifier = modifier.semantics { contentDescription = "Estado: $label" },
        shape = RoundedCornerShape(50),
        color = container
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class StatusTone { NOMINAL, WARNING, CRITICAL, NEUTRAL }

fun statusTone(status: String, sensorAvailable: Boolean = true): StatusTone = when {
    !sensorAvailable -> StatusTone.WARNING
    status == "CRITICAL" -> StatusTone.CRITICAL
    status == "ALERT" -> StatusTone.WARNING
    status == "NOMINAL" -> StatusTone.NOMINAL
    else -> StatusTone.NEUTRAL
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    TGCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TGTextMuted)
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor,
                modifier = Modifier.padding(top = 5.dp))
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TGTextMuted,
                    modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TGTextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium)
    }
}
