package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.AutoAction
import com.jeissonalberto.thermaguard.domain.ThermalUiState

@Composable
fun DashboardScreen(
    uiState: ThermalUiState,
    onToggleMonitor: () -> Unit,
    onToggleAutoMode: () -> Unit
) {
    val snap  = uiState.latest
    val level = snap.batteryTemp.toThermalLevel()

    val bgTop = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF0A1628)
        ThermalLevel.WARM      -> Color(0xFF1A1A2E)
        ThermalLevel.HOT       -> Color(0xFF2D1B00)
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY -> Color(0xFF3D0000)
    }

    val scroll = rememberScrollState()

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(listOf(bgTop, Color(0xFF0F1420))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ThermaGuard", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (uiState.isMonitoring) "Monitoreando en tiempo real" else "Motor en espera",
                        fontSize = 11.sp,
                        color = if (uiState.isMonitoring) Color(0xFF00C853) else Color.White.copy(alpha = 0.4f)
                    )
                }
                // Badge motor AUTO — siempre visible
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF00C853).copy(alpha = 0.12f),
                    modifier = Modifier.border(1.dp, Color(0xFF00C853).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pulse = rememberInfiniteTransition(label = "p")
                        val a by pulse.animateFloat(
                            0.4f, 1f,
                            infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a"
                        )
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF00C853).copy(alpha = a)))
                        Text("AUTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00C853))
                    }
                }
            }

            // ── Termómetro central ───────────────────────────────────────────
            TempGauge(snap = snap, level = level)

            // ── Métricas secundarias ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricChip(
                    modifier = Modifier.weight(1f),
                    label    = "CPU",
                    value    = "${snap.cpuUsage.toInt()}%",
                    icon     = Icons.Default.Memory,
                    color    = when {
                        snap.cpuUsage > 75 -> Color(0xFFFF5252)
                        snap.cpuUsage > 45 -> Color(0xFFFFD600)
                        else               -> Color(0xFF00E676)
                    }
                )
                MetricChip(
                    modifier = Modifier.weight(1f),
                    label    = "Batería",
                    value    = "${snap.batteryLevel}%${if (snap.isCharging) " ⚡" else ""}",
                    icon     = if (snap.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    color    = if (snap.isCharging) Color(0xFFFFD600) else Color(0xFF00E676)
                )
                MetricChip(
                    modifier = Modifier.weight(1f),
                    label    = "RAM usada",
                    value    = "${snap.ramUsageMb} MB",
                    icon     = Icons.Default.Storage,
                    color    = when {
                        snap.ramUsageMb > 3500 -> Color(0xFFFF5252)
                        snap.ramUsageMb > 2500 -> Color(0xFFFFD600)
                        else                   -> Color(0xFF00E676)
                    }
                )
            }

            // ── Predicción del motor ─────────────────────────────────────────
            uiState.prediction?.let { pred ->
                if (pred.confidence != PredictionConfidence.LOW && pred.predictedTemp > 0f) {
                    PredictionBanner(pred = pred)
                }
            }

            // ── Causas de calor ──────────────────────────────────────────────
            if (uiState.causes.isNotEmpty()) {
                CausesSection(causes = uiState.causes)
            }

            // ── Última acción del motor ──────────────────────────────────────
            if (uiState.autoActionsLog.isNotEmpty()) {
                LastAutoActionBanner(action = uiState.autoActionsLog.first())
            }

            // ── Tips del motor (máx 2) ───────────────────────────────────────
            val tips = uiState.smartTips.take(2)
            if (tips.isNotEmpty()) {
                TipsRow(tips = tips)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Componentes ─────────────────────────────────────────────────────────────

@Composable
fun TempGauge(snap: ThermalSnapshot, level: ThermalLevel) {
    val color = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF00E676)
        ThermalLevel.WARM      -> Color(0xFFFFD600)
        ThermalLevel.HOT       -> Color(0xFFFF6D00)
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY -> Color(0xFFFF1744)
    }
    val pulse = rememberInfiniteTransition(label = "gaugePulse")
    val scale by pulse.animateFloat(
        1f,
        if (level == ThermalLevel.CRITICAL || level == ThermalLevel.EMERGENCY) 1.04f else 1f,
        infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse), label = "s"
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape  = CircleShape,
            color  = color.copy(alpha = 0.08f),
            modifier = Modifier
                .size(180.dp)
                .scale(scale)
                .border(2.dp, color.copy(alpha = 0.4f), CircleShape)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(level.emoji, fontSize = 36.sp)
                Text(
                    "${snap.batteryTemp}°C",
                    fontSize    = 42.sp,
                    fontWeight  = FontWeight.ExtraBold,
                    color       = color
                )
                Text(
                    level.label,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color      = color.copy(alpha = 0.8f)
                )
                if (snap.topApp.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(snap.topApp, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
fun MetricChip(
    modifier: Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(14.dp),
        color    = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun PredictionBanner(pred: TempPrediction) {
    val rising = pred.slope > 0
    val trendColor = if (rising) Color(0xFFFF6D00) else Color(0xFF00C853)
    val confidenceLabel = when (pred.confidence) {
        PredictionConfidence.HIGH   -> "Alta confianza"
        PredictionConfidence.MEDIUM -> "Confianza media"
        else                        -> "Datos iniciales"
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, trendColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (rising) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                null, tint = trendColor, modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Predicción — $confidenceLabel",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                Text("${pred.predictedTemp}°C esperados · ${pred.trendText}",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }
    }
}

@Composable
fun CausesSection(causes: List<HeatCause>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Por qué está caliente", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.6f))
        causes.take(3).forEach { cause ->
            val color = when {
                cause.severity >= 3 -> Color(0xFFFF5252)
                cause.severity == 2 -> Color(0xFFFFD600)
                else                -> Color(0xFF69F0AE)
            }
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = color.copy(alpha = 0.07f),
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(11.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text(cause.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    if (cause.description.isNotEmpty()) {
                        Text(cause.description, fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f))
                    }
                }
            }
        }
    }
}

@Composable
fun LastAutoActionBanner(action: AutoAction) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF00C853).copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, Color(0xFF00C853).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.AutoFixHigh, null, tint = Color(0xFF00C853), modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Motor actuó recientemente", fontSize = 10.sp, color = Color(0xFF00C853).copy(alpha = 0.7f))
                Text(action.description, fontSize = 12.sp, color = Color.White, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun TipsRow(tips: List<SmartTip>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sugerencias del motor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.6f))
        tips.forEach { tip ->
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = Color.White.copy(alpha = 0.04f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(tip.icon, fontSize = 18.sp)
                    Text(tip.title, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), lineHeight = 17.sp)
                }
            }
        }
    }
}
