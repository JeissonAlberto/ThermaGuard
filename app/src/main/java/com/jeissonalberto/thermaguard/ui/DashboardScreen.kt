package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.AutoAction
import com.jeissonalberto.thermaguard.domain.ThermalUiState

// ─────────────────────────────────────────────────────────────────────────────
//  DESIGN TOKENS
// ─────────────────────────────────────────────────────────────────────────────
object TG {
    // Backgrounds
    val bg          = Color(0xFF080C14)
    val surface     = Color(0xFF0F1623)
    val glass       = Color(0x14FFFFFF)
    val glassBorder = Color(0x1AFFFFFF)

    // Accent palette per thermal level
    fun accentFor(level: ThermalLevel) = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF00E5FF)
        ThermalLevel.WARM      -> Color(0xFFFFD740)
        ThermalLevel.HOT       -> Color(0xFFFF6D00)
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY -> Color(0xFFFF1744)
    }

    fun glowFor(level: ThermalLevel) = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF00E5FF).copy(alpha = 0.25f)
        ThermalLevel.WARM      -> Color(0xFFFFD740).copy(alpha = 0.20f)
        ThermalLevel.HOT       -> Color(0xFFFF6D00).copy(alpha = 0.22f)
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY -> Color(0xFFFF1744).copy(alpha = 0.28f)
    }

    val green   = Color(0xFF00E676)
    val amber   = Color(0xFFFFAB40)
    val red     = Color(0xFFFF5252)
    val purple  = Color(0xFFCE93D8)
    val teal    = Color(0xFF80CBC4)
    val textPri = Color(0xFFF0F4FF)
    val textSec = Color(0x80F0F4FF)
    val textDim = Color(0x40F0F4FF)
}

// ─────────────────────────────────────────────────────────────────────────────
//  GLASS CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = TG.glassBorder,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier  = modifier.border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(cornerRadius)),
        shape     = RoundedCornerShape(cornerRadius),
        color     = TG.glass,
        tonalElevation = 0.dp
    ) {
        Column(content = content)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DASHBOARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DashboardScreen(
    uiState: ThermalUiState,
    onToggleMonitor: () -> Unit,
    onToggleAutoMode: () -> Unit
) {
    val snap  = uiState.latest
    val level = snap.batteryTemp.toThermalLevel()
    val accent = TG.accentFor(level)
    val glow   = TG.glowFor(level)
    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors  = listOf(glow, TG.bg),
                    center  = Offset(0.5f, 0.15f),
                    radius  = 900f
                )
            )
    ) {
        // Decorative orb
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-60).dp, y = (-80).dp)
                .blur(80.dp)
                .background(glow.copy(alpha = 0.4f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── TOP BAR ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ThermaGuard",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = TG.textPri,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        if (uiState.isMonitoring) "Motor activo — aprendiendo" else "Iniciando…",
                        fontSize = 12.sp, color = if (uiState.isMonitoring) TG.green else TG.textDim
                    )
                }
                AutoBadge(accent = accent)
            }

            // ── GAUGE ─────────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PremiumGauge(snap = snap, level = level, accent = accent)
            }

            // ── METRIC ROW ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label    = "CPU",
                    value    = "${snap.cpuUsage.toInt()}%",
                    icon     = Icons.Default.Memory,
                    color    = when {
                        snap.cpuUsage > 75 -> TG.red
                        snap.cpuUsage > 45 -> TG.amber
                        else               -> TG.green
                    }
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label    = "Batería",
                    value    = "${snap.batteryLevel}%",
                    icon     = if (snap.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    color    = if (snap.isCharging) TG.amber else TG.green,
                    badge    = if (snap.isCharging) "⚡" else null
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label    = "RAM libre",
                    value    = "${snap.ramUsageMb}MB",
                    icon     = Icons.Default.Memory,
                    color    = when {
                        snap.ramUsageMb < 400  -> TG.red
                        snap.ramUsageMb < 900  -> TG.amber
                        else                   -> TG.green
                    }
                )
            }

            // ── PREDICTION ────────────────────────────────────────────────
            uiState.prediction?.let { pred ->
                if (pred.confidence != PredictionConfidence.LOW && pred.predictedTemp > 0f) {
                    PredictionCard(pred = pred, accent = accent)
                }
            }

            // ── HEAT CAUSES ───────────────────────────────────────────────
            if (uiState.causes.isNotEmpty()) {
                HeatCausesCard(causes = uiState.causes)
            }

            // ── LAST AUTO ACTION ──────────────────────────────────────────
            if (uiState.autoActionsLog.isNotEmpty()) {
                AutoActionBanner(action = uiState.autoActionsLog.first())
            }

            // ── SMART TIPS ────────────────────────────────────────────────
            val tips = uiState.smartTips.take(2)
            if (tips.isNotEmpty()) {
                SmartTipsCard(tips = tips)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PREMIUM GAUGE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PremiumGauge(snap: ThermalSnapshot, level: ThermalLevel, accent: Color) {
    val pulse = rememberInfiniteTransition(label = "gauge")
    val ringAlpha by pulse.animateFloat(
        0.3f,
        if (level == ThermalLevel.CRITICAL || level == ThermalLevel.EMERGENCY) 0.9f else 0.5f,
        infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Reverse), label = "r"
    )
    val scale by pulse.animateFloat(
        1f,
        if (level == ThermalLevel.CRITICAL || level == ThermalLevel.EMERGENCY) 1.05f else 1.01f,
        infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Reverse), label = "s"
    )

    Box(
        modifier          = Modifier.size(220.dp).scale(scale),
        contentAlignment  = Alignment.Center
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(accent.copy(alpha = ringAlpha * 0.3f), Color.Transparent))
                )
        )
        // Mid ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .border(2.dp, Brush.sweepGradient(listOf(accent.copy(alpha = 0.0f), accent.copy(alpha = ringAlpha), accent.copy(alpha = 0.0f))), CircleShape)
        )
        // Inner glass
        Surface(
            modifier  = Modifier.size(170.dp),
            shape     = CircleShape,
            color     = TG.glass,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier         = Modifier.fillMaxSize().border(1.5.dp, accent.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(level.emoji, fontSize = 28.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${snap.batteryTemp}°C",
                        fontSize   = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = accent,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        level.label.uppercase(),
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = accent.copy(alpha = 0.7f),
                        letterSpacing = 2.sp
                    )
                    if (snap.topApp.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(snap.topApp, fontSize = 9.sp, color = TG.textDim)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AUTO BADGE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AutoBadge(accent: Color) {
    val pulse = rememberInfiniteTransition(label = "badge")
    val a by pulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.1f),
        modifier = Modifier.border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent.copy(alpha = a)))
            Text("AUTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.5.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  METRIC TILE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MetricTile(
    modifier: Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    badge: String? = null
) {
    GlassCard(modifier = modifier, accent = color, cornerRadius = 16.dp) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                if (badge != null) {
                    Text(badge, fontSize = 8.sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
                }
            }
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TG.textPri)
            Text(label, fontSize = 10.sp, color = TG.textSec, letterSpacing = 0.5.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PREDICTION CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PredictionCard(pred: TempPrediction, accent: Color) {
    val rising = pred.slope > 0
    val trendColor = if (rising) TG.red else TG.green
    GlassCard(modifier = Modifier.fillMaxWidth(), accent = trendColor) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(trendColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (rising) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    null, tint = trendColor, modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Predicción del motor", fontSize = 10.sp, color = TG.textSec, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(2.dp))
                Text("${pred.predictedTemp}°C esperados",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                Text(pred.trendText, fontSize = 11.sp, color = TG.textSec)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = trendColor.copy(alpha = 0.15f)) {
                Text(
                    when (pred.confidence) {
                        PredictionConfidence.HIGH   -> "Alta"
                        PredictionConfidence.MEDIUM -> "Media"
                        else                        -> "Baja"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = trendColor
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HEAT CAUSES
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HeatCausesCard(causes: List<HeatCause>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Whatshot, null, tint = TG.amber, modifier = Modifier.size(16.dp))
                Text("Por qué está caliente", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
            }
            causes.take(3).forEach { cause ->
                val c = when {
                    cause.severity >= 3 -> TG.red
                    cause.severity == 2 -> TG.amber
                    else                -> TG.teal
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
                        .border(1.dp, c.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(c))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cause.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TG.textPri)
                        if (cause.description.isNotEmpty())
                            Text(cause.description, fontSize = 10.sp, color = TG.textSec, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AUTO ACTION BANNER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AutoActionBanner(action: AutoAction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TG.green.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .border(1.dp, TG.green.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(TG.green.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoFixHigh, null, tint = TG.green, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Motor actuó automáticamente", fontSize = 10.sp, color = TG.green.copy(alpha = 0.7f), letterSpacing = 0.3.sp)
            Text(action.description, fontSize = 12.sp, color = TG.textPri, lineHeight = 16.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SMART TIPS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SmartTipsCard(tips: List<SmartTip>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = TG.purple, modifier = Modifier.size(16.dp))
                Text("Sugerencias del motor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
            }
            tips.forEach { tip ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(tip.icon, fontSize = 18.sp, modifier = Modifier.padding(top = 1.dp))
                    Column {
                        Text(tip.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TG.textPri)
                        Text(tip.detail, fontSize = 11.sp, color = TG.textSec, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}
