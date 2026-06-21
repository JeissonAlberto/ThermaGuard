package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.AutoAction
import com.jeissonalberto.thermaguard.domain.ThermalUiState
import java.text.SimpleDateFormat
import java.util.*
import com.jeissonalberto.thermaguard.ui.theme.LocalTgColors

@Composable
fun AlertsScreen(
    uiState: ThermalUiState,
    onThresholdChange: (Float) -> Unit,
    onClearLog: () -> Unit
) {
    val tg = LocalTgColors.current
    val snap   = uiState.latest
    val mainTemp = if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp
    val level  = mainTemp.toThermalLevel()
    val accent = TG.accentFor(level)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(
                colors  = listOf(TG.glowFor(level).copy(alpha = 0.15f), tg.bg),
                center  = Offset(0.5f, 0.0f),
                radius  = 700f
            ))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

            // ── HEADER ────────────────────────────────────────────────────
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Alertas", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = tg.textPri, letterSpacing = (-0.5).sp)
                    Text("Motor autónomo — siempre activo", fontSize = 12.sp, color = TG.green)
                }
                ModeBadge(mode = uiState.operationMode, accent = accent)
            }
            Spacer(Modifier.height(16.dp))

            // ── THRESHOLD ─────────────────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = accent) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, null, tint = TG.purple, modifier = Modifier.size(16.dp))
                            Text("Umbral de acción", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tg.textPri)
                        }
                        Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.15f)) {
                            Text("${uiState.alertThreshold.toInt()}°C",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent)
                        }
                    }
                    Text("El motor actúa automáticamente al superar este valor. Se calibra solo con el uso.",
                        fontSize = 11.sp, color = tg.textSec, lineHeight = 16.sp)
                    Slider(
                        value = uiState.alertThreshold,
                        onValueChange = onThresholdChange,
                        valueRange = 38f..50f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor       = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = accent.copy(alpha = 0.2f)
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("38°C Conservador", fontSize = 9.sp, color = tg.textDim)
                        Text("50°C Agresivo", fontSize = 9.sp, color = tg.textDim)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── LOG HEADER ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, null, tint = tg.textSec, modifier = Modifier.size(15.dp))
                    Text("Acciones del motor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tg.textPri)
                    if (uiState.autoActionsLog.isNotEmpty()) {
                        Surface(shape = CircleShape, color = accent.copy(alpha = 0.15f)) {
                            Text("${uiState.autoActionsLog.size}",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent)
                        }
                    }
                }
                if (uiState.autoActionsLog.isNotEmpty()) {
                    TextButton(onClick = onClearLog, contentPadding = PaddingValues(0.dp)) {
                        Text("Limpiar", fontSize = 11.sp, color = tg.textDim)
                    }
                }
            }

            // ── LOG LIST ──────────────────────────────────────────────────
            if (uiState.autoActionsLog.isEmpty()) {
                EmptyLogState()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.autoActionsLog) { action ->
                        ActionLogCard(action = action)
                    }
                }
            }
        }
    }
}

@Composable
fun ActionLogCard(action: AutoAction) {
    val sdf = remember { SimpleDateFormat("HH:mm · dd/MM", Locale.getDefault()) }
    val isWarning = action.description.startsWith("⚠️")
    val color = if (isWarning) TG.amber else TG.green

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isWarning) Icons.Default.Warning else Icons.Default.AutoFixHigh,
                null, tint = color, modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(action.description, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = tg.textPri, lineHeight = 18.sp)
            Text("Activado por: ${action.trigger}", fontSize = 10.sp, color = tg.textSec)
        }
        Text(sdf.format(Date(action.timestamp)), fontSize = 9.sp, color = tg.textDim,
            modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun EmptyLogState() {
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(TG.green.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = TG.green, modifier = Modifier.size(28.dp))
            }
            Text("Todo en orden", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = tg.textPri)
            Text("El motor actúa cuando detecta calor anormal.\nAquí verás cada acción que ejecute.",
                fontSize = 12.sp, color = tg.textSec, lineHeight = 17.sp, textAlign = TextAlign.Center)
        }
    }
}
