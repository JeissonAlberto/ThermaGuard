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
    val bg          = Color(0xFF080C14)
    val surface     = Color(0xFF0F1623)
    val glass       = Color(0x14FFFFFF)
    val glassBorder = Color(0x1AFFFFFF)

    fun accentFor(level: ThermalLevel) = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF00E5FF)
        ThermalLevel.WARM      -> Color(0xFFFFD740)
        ThermalLevel.HOT       -> Color(0xFFFF6D00)
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY -> Color(0xFFFF1744)
    }
    fun glowFor(level: ThermalLevel) = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF00E5FF).copy(alpha = 0.20f)
        ThermalLevel.WARM      -> Color(0xFFFFD740).copy(alpha = 0.16f)
        ThermalLevel.HOT       -> Color(0xFFFF6D00).copy(alpha = 0.18f)
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY -> Color(0xFFFF1744).copy(alpha = 0.22f)
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

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = TG.glassBorder,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(cornerRadius)),
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
    onToggleAutoMode: () -> Unit
) {
    val snap   = uiState.latest
    val level  = snap.batteryTemp.toThermalLevel()
    val accent = TG.accentFor(level)
    val glow   = TG.glowFor(level)
    val scroll = rememberScrollState()

    val inf = rememberInfiniteTransition(label = "dash")
    val orbAlpha by inf.animateFloat(0.6f, 1f,
        infiniteRepeatable(tween(2200, easing = EaseInOut), RepeatMode.Reverse), label = "oa")

    Box(
        modifier = Modifier.fillMaxSize().background(TG.bg)
    ) {
        // Orb de ambiente dinámico
        Box(
            modifier = Modifier
                .size(380.dp)
                .offset(x = (-40).dp, y = (-60).dp)
                .blur(90.dp)
                .background(glow.copy(alpha = orbAlpha * 0.55f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── HEADER ────────────────────────────────────────────────────
            HeaderBar(uiState = uiState, accent = accent)

            // ── GAUGE CENTRAL ─────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PremiumGauge(snap = snap, level = level, accent = accent, uiState = uiState)
            }

            // ── ESTADO RÁPIDO (1 línea) ───────────────────────────────────
            QuickStatusBar(snap = snap, level = level, accent = accent)

            // ── MÉTRICAS 3 EN FILA ────────────────────────────────────────
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

            // ── PREDICCIÓN ────────────────────────────────────────────────
            uiState.prediction?.let { pred ->
                if (pred.confidence != PredictionConfidence.LOW && pred.predictedTemp > 0f) {
                    PredictionCard(pred = pred, accent = accent)
                }
            }

            // ── POR QUÉ ESTÁ CALIENTE ─────────────────────────────────────
            if (uiState.causes.isNotEmpty()) {
                HeatCausesCard(causes = uiState.causes)
            }

            // ── ACCIÓN AUTOMÁTICA ─────────────────────────────────────────
            if (uiState.autoActionsLog.isNotEmpty()) {
                AutoActionBanner(action = uiState.autoActionsLog.first())
            }

            // ── SUGERENCIAS DEL MOTOR ─────────────────────────────────────
            val tips = uiState.smartTips.take(2)
            if (tips.isNotEmpty()) {
                SmartTipsCard(tips = tips)
            }

            // ── MODO JUEGO ───────────────────────────────────────────────────
            if (uiState.gameModeState.isActive) {
                GameModeBanner(gameMode = uiState.gameModeState)
            }
            // ── CARGA SEGURA ──────────────────────────────────────────────────
            if (uiState.safeChargeState.isCharging) {
                SafeChargeBanner(safeCharge = uiState.safeChargeState)
            }
            // ── ENFRIANDO ─────────────────────────────────────────────────────
            if (uiState.isCoolingDown) {
                CoolingAnimation()
            }
            // ── FOOTER JASOL ──────────────────────────────────────────────
            JasolFooter()

            Spacer(Modifier.height(8.dp))
        }
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
            Text(
                "ThermaGuard",
                fontSize      = 20.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = TG.textPri,
                letterSpacing = (-0.5).sp
            )
            Text(
                "by Jasol Group",
                fontSize = 10.sp,
                color    = accent.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp
            )
        }
        AutoBadge(accent = accent, isMonitoring = uiState.isMonitoring)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GAUGE PREMIUM — con arco de progreso + Risk Score
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

    // Temperatura animada (suave entre valores)
    val tempAnim by animateFloatAsState(snap.batteryTemp, tween(900, easing = EaseOutCubic), label = "t")

    // Glow pulsante en crítico
    val glowAlpha by pulse.animateFloat(
        if (isCritical) 0.4f else 0.15f,
        if (isCritical) 0.85f else 0.30f,
        infiniteRepeatable(tween(if (isCritical) 700 else 1800, easing = EaseInOut), RepeatMode.Reverse), label = "gl"
    )

    // Rango del termómetro: 25°C = 0%, 55°C = 100%
    val minTemp = 25f
    val maxTemp = 55f
    val tempPct = ((tempAnim - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)

    // Arco: 240° de barrido, empieza en 150° (abajo-izquierda)
    val arcStart  = 150f
    val arcSweep  = 240f
    val needleAngle = arcStart + tempPct * arcSweep

    val riskScore = uiState.profile?.riskScore ?: 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.52f
            val radius = size.width.coerceAtMost(size.height) * 0.42f
            val strokeW = radius * 0.085f

            // ── Glow exterior ──────────────────────────────────────────
            drawArc(
                color      = accent.copy(alpha = glowAlpha * 0.35f),
                startAngle = arcStart,
                sweepAngle = arcSweep,
                useCenter  = false,
                topLeft    = Offset(cx - radius - strokeW * 2, cy - radius - strokeW * 2),
                size       = Size((radius + strokeW * 2) * 2, (radius + strokeW * 2) * 2),
                style      = Stroke(width = strokeW * 3.5f, cap = StrokeCap.Round)
            )

            // ── Track (fondo gris) ─────────────────────────────────────
            drawArc(
                color      = Color.White.copy(alpha = 0.07f),
                startAngle = arcStart,
                sweepAngle = arcSweep,
                useCenter  = false,
                topLeft    = Offset(cx - radius, cy - radius),
                size       = Size(radius * 2, radius * 2),
                style      = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // ── Arco de progreso con gradiente de color ────────────────
            // Dividido en 3 segmentos: verde→amarillo→rojo
            val seg = arcSweep / 3f
            listOf(
                Triple(arcStart,          seg,  android.graphics.Color.parseColor("#00E676")),
                Triple(arcStart + seg,    seg,  android.graphics.Color.parseColor("#FFD740")),
                Triple(arcStart + seg*2f, seg,  android.graphics.Color.parseColor("#FF1744")),
            ).forEach { (start, sweep, col) ->
                val segEnd   = start + sweep
                val progEnd  = arcStart + tempPct * arcSweep
                val drawSweep = (progEnd - start).coerceIn(0f, sweep)
                if (drawSweep > 0f) {
                    drawArc(
                        color      = Color(col).copy(alpha = 0.9f),
                        startAngle = start,
                        sweepAngle = drawSweep,
                        useCenter  = false,
                        topLeft    = Offset(cx - radius, cy - radius),
                        size       = Size(radius * 2, radius * 2),
                        style      = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }

            // ── Marcas de tick ─────────────────────────────────────────
            val tickTemps = listOf(25f, 30f, 35f, 40f, 45f, 50f, 55f)
            tickTemps.forEach { t ->
                val pct   = (t - minTemp) / (maxTemp - minTemp)
                val angle = Math.toRadians((arcStart + pct * arcSweep).toDouble())
                val isMajor = t % 5 == 0f
                val outerR = radius - strokeW * 0.8f
                val innerR = outerR - if (isMajor) strokeW * 1.2f else strokeW * 0.6f
                drawLine(
                    color       = Color.White.copy(alpha = if (isMajor) 0.5f else 0.25f),
                    start       = Offset(cx + (outerR * kotlin.math.cos(angle)).toFloat(), cy + (outerR * kotlin.math.sin(angle)).toFloat()),
                    end         = Offset(cx + (innerR * kotlin.math.cos(angle)).toFloat(), cy + (innerR * kotlin.math.sin(angle)).toFloat()),
                    strokeWidth = if (isMajor) 2.5f else 1.5f
                )
            }

            // ── Aguja ──────────────────────────────────────────────────
            val needleRad = Math.toRadians(needleAngle.toDouble())
            val needleLen = radius - strokeW * 2.5f
            val nx = cx + (needleLen * kotlin.math.cos(needleRad)).toFloat()
            val ny = cy + (needleLen * kotlin.math.sin(needleRad)).toFloat()

            // Sombra de aguja
            drawLine(
                color       = Color.Black.copy(alpha = 0.4f),
                start       = Offset(cx + 3f, cy + 3f),
                end         = Offset(nx + 3f, ny + 3f),
                strokeWidth = strokeW * 0.18f,
                cap         = StrokeCap.Round
            )
            // Aguja principal
            drawLine(
                color       = accent,
                start       = Offset(cx, cy),
                end         = Offset(nx, ny),
                strokeWidth = strokeW * 0.16f,
                cap         = StrokeCap.Round
            )
            // Centro de aguja
            drawCircle(accent,          strokeW * 0.35f, Offset(cx, cy))
            drawCircle(Color(0xFF080C14), strokeW * 0.18f, Offset(cx, cy))

            // ── Punto de glow en la punta ──────────────────────────────
            drawCircle(accent.copy(alpha = glowAlpha), strokeW * 0.8f, Offset(nx, ny))
            drawCircle(accent,                          strokeW * 0.25f, Offset(nx, ny))
        }

        // ── Texto central (sobre el Canvas) ───────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(level.emoji, fontSize = 18.sp)
            AnimatedContent(
                targetState = snap.batteryTemp,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "tempTxt"
            ) { t ->
                Text(
                    "${t}°C",
                    fontSize      = 42.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = accent,
                    letterSpacing = (-1.5).sp
                )
            }
            Text(
                level.label.uppercase(),
                fontSize      = 9.sp,
                fontWeight    = FontWeight.Bold,
                color         = accent.copy(alpha = 0.6f),
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(4.dp))
            RiskMiniBar(risk = riskScore.toFloat().coerceIn(0f, 100f), accent = accent)
            Text("Riesgo ${riskScore}%", fontSize = 8.sp, color = TG.textDim)
        }

        // ── Labels 25° y 55° ──────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            Text("25°", fontSize = 9.sp, color = TG.textDim,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 12.dp))
            Text("55°", fontSize = 9.sp, color = TG.red.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 12.dp))
        }
    }
}


@Composable
fun RiskMiniBar(risk: Float, accent: Color) {
    Box(
        modifier = Modifier
            .width(90.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.1f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(risk / 100f)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(TG.green, TG.amber, TG.red)
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  QUICK STATUS BAR — 1 línea de contexto intuitivo
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun QuickStatusBar(snap: ThermalSnapshot, level: ThermalLevel, accent: Color) {
    val msg = when {
        level == ThermalLevel.EMERGENCY -> "⚠️ Temperatura crítica — pon el teléfono a descansar"
        level == ThermalLevel.CRITICAL  -> "🔴 Demasiado caliente — cierra apps y quita la funda"
        level == ThermalLevel.HOT       -> "🟠 Caliente — evita uso intensivo por ahora"
        level == ThermalLevel.WARM      -> "🟡 Tibio — el motor está aprendiendo tu patrón"
        snap.isCharging                 -> "⚡ Cargando — temperatura normal"
        else                            -> "✅ Todo bien — dispositivo en zona segura"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        color    = accent.copy(alpha = 0.08f),
        border   = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Text(
            msg,
            modifier  = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            fontSize  = 13.sp,
            color     = TG.textPri,
            lineHeight = 18.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AUTO BADGE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AutoBadge(accent: Color, isMonitoring: Boolean = true) {
    val pulse = rememberInfiniteTransition(label = "badge")
    val a by pulse.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Surface(
        shape    = RoundedCornerShape(20.dp),
        color    = accent.copy(alpha = 0.1f),
        modifier = Modifier.border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape)
                .background(if (isMonitoring) accent.copy(alpha = a) else TG.textDim))
            Text(
                if (isMonitoring) "AUTO" else "OFF",
                fontSize      = 10.sp,
                fontWeight    = FontWeight.Bold,
                color         = if (isMonitoring) accent else TG.textDim,
                letterSpacing = 1.5.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  METRIC TILE — con sublabel
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
    GlassCard(modifier = modifier, accent = color, cornerRadius = 16.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                if (badge != null)
                    Text(badge, fontSize = 8.sp,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
            }
            Text(value,
                fontSize   = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = TG.textPri,
                textAlign  = TextAlign.Center
            )
            Text(label,
                fontSize  = 9.sp,
                color     = TG.textSec,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
            if (sublabel.isNotEmpty()) {
                Text(sublabel,
                    fontSize  = 8.sp,
                    color     = color.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 10.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PREDICTION CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PredictionCard(pred: TempPrediction, accent: Color) {
    val rising = pred.slope > 0
    val tColor = if (rising) TG.red else TG.green
    GlassCard(modifier = Modifier.fillMaxWidth(), accent = tColor) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(tColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (rising) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    null, tint = tColor, modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Predicción del motor",
                    fontSize = 10.sp, color = TG.textSec, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(2.dp))
                Text("${pred.predictedTemp}°C esperados",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                Text(pred.trendText, fontSize = 11.sp, color = TG.textSec)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = tColor.copy(alpha = 0.15f)) {
                Text(
                    when (pred.confidence) {
                        PredictionConfidence.HIGH   -> "Alta"
                        PredictionConfidence.MEDIUM -> "Media"
                        else                        -> "Baja"
                    },
                    modifier  = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize  = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color     = tColor
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
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Whatshot, null, tint = TG.amber, modifier = Modifier.size(16.dp))
                Text("¿Por qué está caliente?",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
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
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(TG.green.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
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
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = TG.purple, modifier = Modifier.size(16.dp))
                Text("Sugerencias del motor",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
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


// ─────────────────────────────────────────────────────────────────────────────
//  MODO JUEGO BANNER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GameModeBanner(gameMode: GameModeState) {
    val inf = rememberInfiniteTransition(label = "game")
    val glow by inf.animateFloat(0.3f, 0.7f,
        infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse), label = "g")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF7B1FA2).copy(alpha = 0.15f), Color(0xFF1565C0).copy(alpha = 0.15f))),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp,
                Brush.horizontalGradient(listOf(Color(0xFFCE93D8).copy(alpha = glow), Color(0xFF90CAF9).copy(alpha = glow))),
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🎮", fontSize = 22.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text("Modo Juego activo",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TG.textPri)
            Text("Umbrales ajustados para gaming · ${gameMode.detectedGame.take(20)}",
                fontSize = 10.sp, color = TG.textSec)
        }
        Surface(shape = RoundedCornerShape(8.dp),
            color = Color(0xFFCE93D8).copy(alpha = 0.15f)) {
            Text("46°C", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCE93D8))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MODO CARGA SEGURA BANNER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SafeChargeBanner(safeCharge: SafeChargeState) {
    val color = if (safeCharge.isOverheating) TG.amber else TG.green
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            if (safeCharge.isOverheating) Icons.Default.Warning else Icons.Default.BatteryChargingFull,
            null, tint = color, modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("Carga Segura",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TG.textPri)
            Text(safeCharge.recommendation,
                fontSize = 10.sp, color = TG.textSec, lineHeight = 14.sp)
        }
        Text("${safeCharge.chargingTemp.toInt()}°C",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ANIMACIÓN ENFRIAMIENTO
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CoolingAnimation() {
    val inf = rememberInfiniteTransition(label = "cool")
    val scale by inf.animateFloat(0.95f, 1.05f,
        infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse), label = "s")
    val alpha by inf.animateFloat(0.4f, 0.9f,
        infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse), label = "a")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TG.teal.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(1.dp, TG.teal.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("❄️", fontSize = 22.sp, modifier = Modifier.scale(scale).alpha(alpha))
        Column(modifier = Modifier.weight(1f)) {
            Text("Enfriando...",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TG.teal)
            Text("El dispositivo está bajando de temperatura. ¡Bien hecho!",
                fontSize = 10.sp, color = TG.textSec, lineHeight = 14.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  JASOL FOOTER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun JasolFooter() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Jasol Group  ·  Motor v4",
            fontSize  = 9.sp,
            color     = TG.textDim,
            letterSpacing = 0.5.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HELPERS
// ─────────────────────────────────────────────────────────────────────────────
private fun cpuLabel(cpu: Float) = when {
    cpu < 1f  -> "Sin lectura"
    cpu > 75f -> "Alta"
    cpu > 45f -> "Moderada"
    else      -> "Normal"
}

private fun ramLabel(mb: Int) = when {
    mb <= 0    -> "Sin lectura"
    mb < 400   -> "Muy poca"
    mb < 900   -> "Ajustada"
    else       -> "Disponible"
}
