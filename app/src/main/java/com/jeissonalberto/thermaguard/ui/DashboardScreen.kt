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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val bg          = Color(0xFF070B12)
    val surface     = Color(0xFF0D1520)
    val glass       = Color(0x12FFFFFF)
    val glassBorder = Color(0x18FFFFFF)

    fun accentFor(level: ThermalLevel) = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF00E5FF)
        ThermalLevel.WARM      -> Color(0xFFFFD740)
        ThermalLevel.HOT       -> Color(0xFFFF6D00)
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY -> Color(0xFFFF1744)
    }
    fun glowFor(level: ThermalLevel) = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF00E5FF).copy(alpha = 0.18f)
        ThermalLevel.WARM      -> Color(0xFFFFD740).copy(alpha = 0.14f)
        ThermalLevel.HOT       -> Color(0xFFFF6D00).copy(alpha = 0.16f)
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY -> Color(0xFFFF1744).copy(alpha = 0.20f)
    }
    val green   = Color(0xFF00E676)
    val amber   = Color(0xFFFFAB40)
    val red     = Color(0xFFFF5252)
    val purple  = Color(0xFFCE93D8)
    val teal    = Color(0xFF80CBC4)
    val blue    = Color(0xFF82B1FF)
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
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(cornerRadius)),
        shape    = RoundedCornerShape(cornerRadius),
        color    = TG.glass
    ) { Column(content = content) }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DASHBOARD PRINCIPAL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DashboardScreen(
    uiState: ThermalUiState,
    onToggleMonitor: () -> Unit,
    onToggleAutoMode: () -> Unit,
    onSetMode: (OperationMode) -> Unit = {}
) {
    val snap   = uiState.latest
    val level  = snap.batteryTemp.toThermalLevel()
    val accent = TG.accentFor(level)
    val glow   = TG.glowFor(level)
    val scroll = rememberScrollState()

    val inf = rememberInfiniteTransition(label = "bg")
    val orbAlpha by inf.animateFloat(0.5f, 0.9f,
        infiniteRepeatable(tween(2800, easing = EaseInOut), RepeatMode.Reverse), label = "oa")
    val orb2Alpha by inf.animateFloat(0.3f, 0.7f,
        infiniteRepeatable(tween(3400, easing = EaseInOut), RepeatMode.Reverse), label = "ob")

    Box(modifier = Modifier.fillMaxSize().background(TG.bg)) {
        // Orb principal
        Box(
            modifier = Modifier
                .size(420.dp)
                .offset(x = (-60).dp, y = (-80).dp)
                .blur(100.dp)
                .background(glow.copy(alpha = orbAlpha * 0.5f), CircleShape)
        )
        // Orb secundario (fondo derecho)
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .blur(80.dp)
                .background(accent.copy(alpha = orb2Alpha * 0.08f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeaderBar(uiState = uiState, accent = accent)
            ModeSelector(mode = uiState.operationMode, onSetMode = onSetMode, accent = accent)

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PremiumGauge(snap = snap, level = level, accent = accent, uiState = uiState)
            }

            QuickStatusBar(snap = snap, level = level, accent = accent)

            // Métricas 3 en fila
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label    = "CPU",
                    value    = if (snap.cpuUsage < 1f) "—" else "${snap.cpuUsage.toInt()}%",
                    icon     = Icons.Default.Speed,
                    color    = when {
                        snap.cpuUsage > 75 -> TG.red
                        snap.cpuUsage > 45 -> TG.amber
                        else               -> TG.green
                    },
                    sublabel = cpuLabel(snap.cpuUsage)
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label    = "Batería",
                    value    = "${snap.batteryLevel}%",
                    icon     = if (snap.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    color    = when {
                        snap.batteryLevel < 15 -> TG.red
                        snap.isCharging        -> TG.amber
                        else                   -> TG.green
                    },
                    sublabel = if (snap.isCharging) "Cargando ⚡" else "Normal"
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label    = "RAM libre",
                    value    = if (snap.ramUsageMb > 0) "${snap.ramUsageMb}MB" else "—",
                    icon     = Icons.Default.Memory,
                    color    = when {
                        snap.ramUsageMb in 1..399   -> TG.red
                        snap.ramUsageMb in 400..899 -> TG.amber
                        else                        -> TG.green
                    },
                    sublabel = ramLabel(snap.ramUsageMb)
                )
            }

            SiliconPanel(snap = snap, accent = accent, silicon = uiState.siliconAnalysis)

            uiState.prediction?.let { pred ->
                if (pred.confidence != PredictionConfidence.LOW && pred.predictedTemp > 0f) {
                    PredictionCard(pred = pred, accent = accent)
                }
            }

            if (uiState.causes.isNotEmpty()) HeatCausesCard(causes = uiState.causes)
            if (uiState.autoActionsLog.isNotEmpty()) AutoActionBanner(action = uiState.autoActionsLog.first())
            val tips = uiState.smartTips.take(2)
            if (tips.isNotEmpty()) SmartTipsCard(tips = tips)
            if (uiState.gameModeState.isActive) GameModeBanner(gameMode = uiState.gameModeState)
            if (uiState.safeChargeState.isCharging) SafeChargeBanner(safeCharge = uiState.safeChargeState)
            if (uiState.isCoolingDown) CoolingAnimation()

            if (uiState.coolingRecs.isNotEmpty())
                CoolingRecsCard(recs = uiState.coolingRecs)
            JasolFooter()
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MOTOR v5 — MOORE POWER PANEL (rediseñado)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MooreMetric(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color, textAlign = TextAlign.Center)
        Text(label, fontSize = 8.sp, color = TG.textSec, textAlign = TextAlign.Center, lineHeight = 10.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GAUGE PREMIUM
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PremiumGauge(
    snap: ThermalSnapshot,
    level: ThermalLevel,
    accent: Color,
    uiState: ThermalUiState
) {
    val isCritical = level == ThermalLevel.CRITICAL || level == ThermalLevel.EMERGENCY
    val pulse = rememberInfiniteTransition(label = "gauge")
    val tempAnim by animateFloatAsState(snap.batteryTemp, tween(900, easing = EaseOutCubic), label = "t")
    val glowAlpha by pulse.animateFloat(
        if (isCritical) 0.45f else 0.12f,
        if (isCritical) 0.9f  else 0.28f,
        infiniteRepeatable(tween(if (isCritical) 650 else 2000, easing = EaseInOut), RepeatMode.Reverse), label = "gl"
    )
    val minTemp = 25f; val maxTemp = 55f
    val tempPct = ((tempAnim - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
    val arcStart = 150f; val arcSweep = 240f
    val needleAngle = arcStart + tempPct * arcSweep
    val riskScore = uiState.profile?.riskScore ?: 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(265.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.53f
            val radius = size.width.coerceAtMost(size.height) * 0.41f
            val strokeW = radius * 0.082f

            // Glow exterior pulsante
            drawArc(
                color      = accent.copy(alpha = glowAlpha * 0.3f),
                startAngle = arcStart, sweepAngle = arcSweep, useCenter = false,
                topLeft    = Offset(cx - radius - strokeW * 2.2f, cy - radius - strokeW * 2.2f),
                size       = Size((radius + strokeW * 2.2f) * 2, (radius + strokeW * 2.2f) * 2),
                style      = Stroke(width = strokeW * 4f, cap = StrokeCap.Round)
            )
            // Track fondo
            drawArc(
                color      = Color.White.copy(alpha = 0.06f),
                startAngle = arcStart, sweepAngle = arcSweep, useCenter = false,
                topLeft    = Offset(cx - radius, cy - radius),
                size       = Size(radius * 2, radius * 2),
                style      = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            // Arco de progreso (3 segmentos de color)
            val segColors = listOf(
                Triple(arcStart,           arcSweep / 3f, android.graphics.Color.parseColor("#00E676")),
                Triple(arcStart + arcSweep / 3f, arcSweep / 3f, android.graphics.Color.parseColor("#FFD740")),
                Triple(arcStart + arcSweep * 2f / 3f, arcSweep / 3f, android.graphics.Color.parseColor("#FF5252")),
            )
            val progEnd = arcStart + tempPct * arcSweep
            segColors.forEach { (start, sweep, col) ->
                val drawSweep = (progEnd - start).coerceIn(0f, sweep)
                if (drawSweep > 0f)
                    drawArc(
                        color      = Color(col).copy(alpha = 0.92f),
                        startAngle = start, sweepAngle = drawSweep, useCenter = false,
                        topLeft    = Offset(cx - radius, cy - radius),
                        size       = Size(radius * 2, radius * 2),
                        style      = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
            }
            // Ticks
            listOf(25f, 30f, 35f, 40f, 45f, 50f, 55f).forEach { t ->
                val pct   = (t - minTemp) / (maxTemp - minTemp)
                val ang   = Math.toRadians((arcStart + pct * arcSweep).toDouble())
                val isMaj = t % 5 == 0f
                val outerR = radius - strokeW * 0.9f
                val innerR = outerR - if (isMaj) strokeW * 1.3f else strokeW * 0.65f
                drawLine(
                    color = Color.White.copy(alpha = if (isMaj) 0.55f else 0.22f),
                    start = Offset(cx + (outerR * kotlin.math.cos(ang)).toFloat(), cy + (outerR * kotlin.math.sin(ang)).toFloat()),
                    end   = Offset(cx + (innerR * kotlin.math.cos(ang)).toFloat(), cy + (innerR * kotlin.math.sin(ang)).toFloat()),
                    strokeWidth = if (isMaj) 2.5f else 1.5f
                )
            }
            // Aguja
            val needleRad = Math.toRadians(needleAngle.toDouble())
            val needleLen = radius - strokeW * 2.8f
            val nx = cx + (needleLen * kotlin.math.cos(needleRad)).toFloat()
            val ny = cy + (needleLen * kotlin.math.sin(needleRad)).toFloat()
            // Sombra
            drawLine(Color.Black.copy(alpha = 0.35f),
                Offset(cx + 2.5f, cy + 2.5f), Offset(nx + 2.5f, ny + 2.5f),
                strokeWidth = strokeW * 0.17f, cap = StrokeCap.Round)
            // Aguja principal
            drawLine(accent, Offset(cx, cy), Offset(nx, ny),
                strokeWidth = strokeW * 0.15f, cap = StrokeCap.Round)
            // Hub
            drawCircle(accent, strokeW * 0.38f, Offset(cx, cy))
            drawCircle(Color(0xFF070B12), strokeW * 0.20f, Offset(cx, cy))
            // Punta glow
            drawCircle(accent.copy(alpha = glowAlpha), strokeW * 0.9f, Offset(nx, ny))
            drawCircle(accent, strokeW * 0.28f, Offset(nx, ny))
        }

        // Texto central
        Column(
            modifier = Modifier.align(Alignment.Center).offset(y = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(level.emoji, fontSize = 20.sp)
            AnimatedContent(
                targetState = snap.batteryTemp,
                transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) },
                label = "tempTxt"
            ) { t ->
                Text("${t}°C", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold,
                    color = accent, letterSpacing = (-2).sp)
            }
            Text(level.label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                color = accent.copy(alpha = 0.55f), letterSpacing = 2.5.sp)
            Spacer(Modifier.height(5.dp))
            RiskMiniBar(risk = riskScore.toFloat().coerceIn(0f, 100f), accent = accent)
            Spacer(Modifier.height(2.dp))
            Text("Riesgo ${riskScore}%", fontSize = 8.sp, color = TG.textDim)
        }

        // Labels 25° / 55°
        Box(modifier = Modifier.fillMaxSize()) {
            Text("25°", fontSize = 9.sp, color = TG.textDim,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 8.dp))
            Text("55°", fontSize = 9.sp, color = TG.red.copy(alpha = 0.65f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 8.dp))
        }
    }
}

@Composable
fun RiskMiniBar(risk: Float, accent: Color) {
    Box(modifier = Modifier.width(86.dp).height(4.dp)
        .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.08f))) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(risk / 100f)
            .clip(RoundedCornerShape(2.dp))
            .background(Brush.horizontalGradient(listOf(TG.green, TG.amber, TG.red))))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  QUICK STATUS BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun QuickStatusBar(snap: ThermalSnapshot, level: ThermalLevel, accent: Color) {
    val msg = when {
        level == ThermalLevel.EMERGENCY -> "🚨 Temperatura crítica — pon el teléfono a descansar"
        level == ThermalLevel.CRITICAL  -> "🔴 Demasiado caliente — cierra apps y quita la funda"
        level == ThermalLevel.HOT       -> "🟠 Caliente — evita uso intensivo por ahora"
        level == ThermalLevel.WARM      -> "🟡 Tibio — el motor vigila tu patrón de uso"
        snap.isCharging                 -> "⚡ Cargando — temperatura en rango seguro"
        else                            -> "✅ Todo bien — dispositivo en zona segura"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.07f))
            .border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
        Text(msg, fontSize = 13.sp, color = TG.textPri, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HEADER BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HeaderBar(uiState: ThermalUiState, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("ThermaGuard", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                color = TG.textPri, letterSpacing = (-0.8).sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(accent))
                Text("Jasol Group  ·  Motor v5", fontSize = 10.sp,
                    color = TG.textDim, letterSpacing = 0.3.sp)
            }
        }
        AutoBadge(accent = accent, isMonitoring = uiState.isMonitoring)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AUTO BADGE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AutoBadge(accent: Color, isMonitoring: Boolean = true) {
    val pulse = rememberInfiniteTransition(label = "badge")
    val a by pulse.animateFloat(0.25f, 1f,
        infiniteRepeatable(tween(950), RepeatMode.Reverse), label = "a")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.09f))
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape)
            .background(if (isMonitoring) accent.copy(alpha = a) else TG.textDim))
        Text(
            if (isMonitoring) "AUTO" else "OFF",
            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
            color = if (isMonitoring) accent else TG.textDim, letterSpacing = 1.8.sp
        )
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
    sublabel: String = "",
    badge: String? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TG.glass)
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box {
            Box(modifier = Modifier.size(30.dp).clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }
            if (badge != null)
                Text(badge, fontSize = 8.sp,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
        }
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
            color = TG.textPri, textAlign = TextAlign.Center)
        Text(label, fontSize = 9.sp, color = TG.textSec, letterSpacing = 0.4.sp,
            textAlign = TextAlign.Center)
        if (sublabel.isNotEmpty())
            Text(sublabel, fontSize = 8.sp, color = color.copy(alpha = 0.75f),
                textAlign = TextAlign.Center, lineHeight = 10.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PREDICTION CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PredictionCard(pred: TempPrediction, accent: Color) {
    val rising = pred.slope > 0
    val tColor = if (rising) TG.red else TG.green
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tColor.copy(alpha = 0.07f))
            .border(1.dp, tColor.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape)
            .background(tColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center) {
            Icon(if (rising) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                null, tint = tColor, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Predicción del motor", fontSize = 10.sp, color = TG.textSec, letterSpacing = 0.4.sp)
            Text("${"%.1f".format(pred.predictedTemp)}°C esperados",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
            Text(pred.trendText, fontSize = 11.sp, color = TG.textSec)
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(9.dp))
            .background(tColor.copy(alpha = 0.13f))
            .padding(horizontal = 9.dp, vertical = 5.dp)) {
            Text(when (pred.confidence) {
                PredictionConfidence.HIGH   -> "Alta"
                PredictionConfidence.MEDIUM -> "Media"
                else                        -> "Baja"
            }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tColor)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HEAT CAUSES
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HeatCausesCard(causes: List<HeatCause>) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TG.glass)
            .border(1.dp, TG.glassBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Whatshot, null, tint = TG.amber, modifier = Modifier.size(16.dp))
            Text("¿Por qué está caliente?", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = TG.textPri)
        }
        causes.take(3).forEach { cause ->
            val c = when {
                cause.severity >= 3 -> TG.red
                cause.severity == 2 -> TG.amber
                else                -> TG.teal
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.copy(alpha = 0.07f))
                    .border(1.dp, c.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
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

// ─────────────────────────────────────────────────────────────────────────────
//  AUTO ACTION BANNER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AutoActionBanner(action: AutoAction) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TG.green.copy(alpha = 0.07f))
            .border(1.dp, TG.green.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(38.dp).clip(CircleShape)
            .background(TG.green.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AutoFixHigh, null, tint = TG.green, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Motor actuó automáticamente",
                fontSize = 10.sp, color = TG.green.copy(alpha = 0.7f), letterSpacing = 0.3.sp)
            Text(action.description, fontSize = 12.sp, color = TG.textPri, lineHeight = 16.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SMART TIPS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SmartTipsCard(tips: List<SmartTip>) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TG.glass)
            .border(1.dp, TG.glassBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = TG.purple, modifier = Modifier.size(16.dp))
            Text("Sugerencias del motor", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = TG.textPri)
        }
        tips.forEach { tip ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tip.icon, fontSize = 18.sp, modifier = Modifier.padding(top = 1.dp))
                Column {
                    Text(tip.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TG.textPri)
                    Text(tip.detail, fontSize = 11.sp, color = TG.textSec, lineHeight = 15.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GAME MODE BANNER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GameModeBanner(gameMode: GameModeState) {
    val inf = rememberInfiniteTransition(label = "game")
    val glow by inf.animateFloat(0.25f, 0.65f,
        infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse), label = "g")
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(
                Color(0xFF7B1FA2).copy(alpha = 0.13f), Color(0xFF1565C0).copy(alpha = 0.13f))))
            .border(1.dp,
                Brush.horizontalGradient(listOf(
                    Color(0xFFCE93D8).copy(alpha = glow), Color(0xFF90CAF9).copy(alpha = glow))),
                RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🎮", fontSize = 22.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text("Modo Juego activo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TG.textPri)
            Text("Umbrales ajustados  ·  ${gameMode.detectedGame.take(22)}",
                fontSize = 10.sp, color = TG.textSec)
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFCE93D8).copy(alpha = 0.13f))
            .padding(horizontal = 9.dp, vertical = 5.dp)) {
            Text("46°C", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCE93D8))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SAFE CHARGE BANNER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SafeChargeBanner(safeCharge: SafeChargeState) {
    val color = if (safeCharge.isOverheating) TG.amber else TG.green
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.07f))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(if (safeCharge.isOverheating) Icons.Default.Warning else Icons.Default.BatteryChargingFull,
            null, tint = color, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Carga Segura", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TG.textPri)
            Text(safeCharge.recommendation, fontSize = 10.sp, color = TG.textSec, lineHeight = 14.sp)
        }
        Text("${safeCharge.chargingTemp.toInt()}°C",
            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  COOLING ANIMATION
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CoolingAnimation() {
    val inf = rememberInfiniteTransition(label = "cool")
    val scale by inf.animateFloat(0.92f, 1.06f,
        infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse), label = "s")
    val alpha by inf.animateFloat(0.4f, 0.95f,
        infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse), label = "a")
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TG.teal.copy(alpha = 0.07f))
            .border(1.dp, TG.teal.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("❄️", fontSize = 22.sp, modifier = Modifier.scale(scale).alpha(alpha))
        Column(modifier = Modifier.weight(1f)) {
            Text("Enfriando...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TG.teal)
            Text("Temperatura bajando — el motor lo está gestionando.",
                fontSize = 10.sp, color = TG.textSec, lineHeight = 14.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  JASOL FOOTER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun JasolFooter() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(4.dp).clip(CircleShape)
            .background(TG.textDim.copy(alpha = 0.5f)))
        Spacer(Modifier.width(6.dp))
        Text("Jasol Group  ·  Motor v5", fontSize = 9.sp,
            color = TG.textDim, letterSpacing = 0.6.sp)
        Spacer(Modifier.width(6.dp))
        Box(modifier = Modifier.size(4.dp).clip(CircleShape)
            .background(TG.textDim.copy(alpha = 0.5f)))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HELPERS
// ─────────────────────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────────────────────
//  MOTOR v6 — PANEL DE LAS 3 LEYES DEL SILICIO
//  Datos reales: CPU de /proc/stat, Temp de BatteryManager + /sys/class/thermal
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SiliconPanel(snap: ThermalSnapshot, accent: Color, silicon: SiliconAnalysis?) {
    // Fallback: si silicon es null (aún no calculado), usar Moore simple
    val power  = silicon?.moorePower  ?: run {
        val f = (snap.cpuUsage / 100f).coerceIn(0f, 1f)
        val v = 0.6f + 0.4f * f
        (v * v * f * 100f).coerceIn(0f, 100f)
    }
    val sev = silicon?.severity ?: if (power >= 75f) SiliconSeverity.CRITICAL
              else if (power >= 45f) SiliconSeverity.STRESSED else SiliconSeverity.OPTIMAL
    val panelColor = when (sev) {
        SiliconSeverity.THERMAL_RUNAWAY, SiliconSeverity.CRITICAL -> TG.red
        SiliconSeverity.STRESSED  -> TG.amber
        SiliconSeverity.EFFICIENT -> Color(0xFFFFD740)
        else                      -> TG.green
    }

    val inf = rememberInfiniteTransition(label = "silicon")
    val pulse by inf.animateFloat(0.8f, 1f,
        infiniteRepeatable(tween(1100, easing = EaseInOut), RepeatMode.Reverse), label = "p")
    val moorePct by animateFloatAsState(power / 100f, tween(800, easing = EaseOutCubic), label = "mp")
    val dennardPct by animateFloatAsState((silicon?.dennardEfficiency ?: 80f) / 100f, tween(900, easing = EaseOutCubic), label = "dp")
    val pollackPct by animateFloatAsState((silicon?.pollackPerfPerWatt ?: 70f) / 100f, tween(1000, easing = EaseOutCubic), label = "pp")
    val amdahlPct by animateFloatAsState((silicon?.amdahlThrottleEta ?: 80f) / 100f, tween(950, easing = EaseOutCubic), label = "ap")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TG.glass)
            .border(1.dp, panelColor.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
    ) {
        // Glow de fondo
        Box(
            modifier = Modifier
                .size(130.dp).align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-30).dp).blur(45.dp)
                .background(panelColor.copy(alpha = if (power > 70f) pulse * 0.22f else 0.07f), CircleShape)
        )
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ── Header ──────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(38.dp)
                        .scale(if (power > 70f) pulse else 1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(panelColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Bolt, null, tint = panelColor, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Motor v6  ·  3 Leyes del Silicio",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = TG.textPri, letterSpacing = (-0.2).sp)
                        Text(silicon?.dominantLaw ?: "Moore  ·  Dennard  ·  Pollack  ·  Amdahl",
                            fontSize = 9.sp, color = TG.textSec)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${power.toInt()}",
                        fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                        color = panelColor, letterSpacing = (-1).sp)
                    Text("/ 100", fontSize = 9.sp, color = TG.textDim,
                        modifier = Modifier.offset(y = (-4).dp))
                }
            }

            // ── Fuente de datos reales ───────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DataSourceChip("CPU", "${snap.cpuUsage.toInt()}%", TG.blue)
                DataSourceChip("Temp bat.", "${snap.batteryTemp.toInt()}°C", panelColor)
                DataSourceChip("CPU°", if (snap.cpuTemp > 0f) "${snap.cpuTemp.toInt()}°C" else "—", TG.teal)
                DataSourceChip("Modem°", if (snap.modemTemp > 0f) "${snap.modemTemp.toInt()}°C" else "—", TG.purple)
            }

            // ── 4 barras de las leyes ────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LawBar("⚛️  Moore — P=V²·F", "Carga térmica bruta",
                    moorePct, panelColor, "${power.toInt()}%")
                LawBar("📐  Dennard — Escala de voltaje",
                    if ((silicon?.dennardEfficiency ?: 80f) >= 70f) "Escalado eficiente"
                    else "Fuga de corriente — chip caliente sin carga",
                    dennardPct, TG.teal, "${(silicon?.dennardEfficiency ?: 80f).toInt()}%")
                LawBar("📈  Pollack — Rendimiento real",
                    "Perf útil = √(Potencia) — ${(silicon?.pollackWastedHeat ?: 0f).toInt()}% calor desperdiciado",
                    pollackPct, TG.purple,
                    "${(silicon?.pollackPerfPerWatt ?: 70f).toInt()}%")
                LawBar("⚡  Amdahl — Margen antes de throttle",
                    if ((silicon?.amdahlTimeToThrottle ?: 999) < 120)
                        "Throttle en ~${(silicon?.amdahlTimeToThrottle ?: 0) / 60} min — ${silicon?.amdahlParallelScore?.toInt() ?: 8} núcleos activos"
                    else "Sistema estable — ${silicon?.amdahlParallelScore?.toInt() ?: 8} núcleos activos",
                    amdahlPct,
                    if ((silicon?.amdahlTimeToThrottle ?: 999) < 120) TG.amber else TG.green,
                    "${(silicon?.amdahlThrottleEta ?: 80f).toInt()}%")
            }

            // ── Recomendación ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(panelColor.copy(alpha = 0.07f))
                    .border(1.dp, panelColor.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(silicon?.recommendation ?: "Calculando análisis del silicio...",
                    fontSize = 11.sp, color = TG.textSec, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun LawBar(title: String, subtitle: String, pct: Float, color: Color, valueLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                Text(subtitle, fontSize = 9.sp, color = TG.textSec, lineHeight = 12.sp)
            }
            Text(valueLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color,
                modifier = Modifier.padding(start = 8.dp))
        }
        Box(modifier = Modifier.fillMaxWidth().height(5.dp)
            .clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.06f))) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

@Composable
fun DataSourceChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 8.sp, color = TG.textDim)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SELECTOR DE MODO (LEARNING / AUTO / ACTIVE)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ModeSelector(mode: OperationMode, onSetMode: (OperationMode) -> Unit, accent: Color) {
    val modes = listOf(
        Triple(OperationMode.LEARNING, "Aprendizaje", "🧠"),
        Triple(OperationMode.AUTO,     "Automático",  "⚙️"),
        Triple(OperationMode.ACTIVE,   "Activo",      "🔥")
    )
    val modeColor = when (mode) {
        OperationMode.LEARNING -> TG.blue
        OperationMode.AUTO     -> TG.green
        OperationMode.ACTIVE   -> TG.red
    }
    val modeDesc = when (mode) {
        OperationMode.LEARNING -> "Solo observa y aprende · lecturas cada 60s · bajo consumo"
        OperationMode.AUTO     -> "Actúa según lo aprendido · lecturas cada 30s"
        OperationMode.ACTIVE   -> "Intervención máxima · lecturas cada 15s"
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TG.glass)
            .border(1.dp, modeColor.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Modo de operación", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = TG.textPri)
            Text(modeDesc, fontSize = 9.sp, color = TG.textSec,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                textAlign = TextAlign.End)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (m, label, icon) ->
                val isSelected = m == mode
                val btnColor = when (m) {
                    OperationMode.LEARNING -> TG.blue
                    OperationMode.AUTO     -> TG.green
                    OperationMode.ACTIVE   -> TG.red
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) btnColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
                        .border(1.dp,
                            if (isSelected) btnColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(12.dp))
                        .clickable { onSetMode(m) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(icon, fontSize = 16.sp)
                        Text(label, fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) btnColor else TG.textSec)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  RECOMENDACIONES DE ENFRIAMIENTO
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CoolingRecsCard(recs: List<CoolingRecommendation>) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TG.glass)
            .border(1.dp, TG.teal.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Spa, null, tint = TG.teal, modifier = Modifier.size(16.dp))
            Text("Recomendaciones para enfriar", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = TG.textPri)
            Spacer(Modifier.weight(1f))
            Text("${recs.size} acciones", fontSize = 10.sp, color = TG.textSec)
        }
        recs.take(4).forEach { rec ->
            val impactColor = when {
                rec.impactDegrees >= 3.5f -> TG.green
                rec.impactDegrees >= 2f   -> TG.amber
                else                      -> TG.teal
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(impactColor.copy(alpha = 0.06f))
                    .border(1.dp, impactColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(rec.icon, fontSize = 20.sp, modifier = Modifier.padding(top = 1.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rec.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = TG.textPri, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(Icons.Default.ArrowDownward, null,
                                tint = impactColor, modifier = Modifier.size(11.dp))
                            Text("${rec.impactDegrees}°C", fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, color = impactColor)
                        }
                    }
                    Text(rec.detail, fontSize = 10.sp, color = TG.textSec, lineHeight = 14.sp)
                    // Effort indicator
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(rec.effort) {
                            Box(modifier = Modifier.size(width = 12.dp, height = 3.dp)
                                .clip(RoundedCornerShape(2.dp)).background(impactColor))
                        }
                        repeat(3 - rec.effort) {
                            Box(modifier = Modifier.size(width = 12.dp, height = 3.dp)
                                .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f)))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(when (rec.effort) { 1 -> "Fácil"; 2 -> "Medio"; else -> "Ajuste" },
                            fontSize = 8.sp, color = TG.textDim)
                    }
                }
            }
        }
        if (recs.size > 4) {
            Text("+ ${recs.size - 4} acciones más disponibles",
                fontSize = 10.sp, color = TG.textDim,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center)
        }
    }
}

private fun cpuLabel(cpu: Float) = when {
    cpu < 1f  -> "Sin lectura"
    cpu > 75f -> "Alta"
    cpu > 45f -> "Moderada"
    else      -> "Normal"
}
private fun ramLabel(mb: Int) = when {
    mb <= 0  -> "Sin lectura"
    mb < 400 -> "Muy poca"
    mb < 900 -> "Ajustada"
    else     -> "Disponible"
}
