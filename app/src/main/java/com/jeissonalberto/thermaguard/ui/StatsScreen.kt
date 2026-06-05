package com.jeissonalberto.thermaguard.ui

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.ThermalUiState
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    uiState: ThermalUiState,
    onResetLearning: () -> Unit
) {
    val scroll  = rememberScrollState()
    val level   = uiState.latest.batteryTemp.toThermalLevel()
    val accent  = TG.accentFor(level)
    val profile = uiState.profile

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(
                colors  = listOf(TG.glowFor(level).copy(alpha = 0.10f), TG.bg),
                center  = Offset(1f, 1f),
                radius  = 700f
            ))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── HEADER ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Estadísticas", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = TG.textPri, letterSpacing = (-0.5).sp)
                    Text("Análisis profundo del motor", fontSize = 12.sp, color = TG.textSec)
                }
                IconButton(onClick = onResetLearning) {
                    Icon(Icons.Default.Refresh, null, tint = TG.textDim, modifier = Modifier.size(20.dp))
                }
            }

            // ── RISK SCORE ────────────────────────────────────────────────
            profile?.let { p ->
                RiskScoreCard(profile = p, accent = accent)
            }

            // ── TEMP HISTORY CHART ────────────────────────────────────────
            if (uiState.history.size >= 3) {
                TempHistoryChart(history = uiState.history, accent = accent)
            }

            // ── BATTERY HEALTH ────────────────────────────────────────────
            uiState.batteryHealth?.let { health ->
                BatteryHealthCard(health = health)
            }

            // ── HOURLY HEATMAP ────────────────────────────────────────────
            if (uiState.hourlyProfile.isNotEmpty()) {
                HourlyHeatmapCard(hourly = uiState.hourlyProfile, accent = accent)
            }

            // ── LEARNING PROFILE ──────────────────────────────────────────
            profile?.let { p ->
                LearningProfileCard(profile = p, sampleCount = p.sampleCount)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  RISK SCORE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun RiskScoreCard(profile: LearnedProfile, accent: Color) {
    val score = profile.riskScore.coerceIn(0, 100)
    val scoreColor = when {
        score >= 75 -> TG.red
        score >= 50 -> Color(0xFFFF6D00)
        score >= 25 -> TG.amber
        else        -> TG.green
    }
    val scoreLabel = when {
        score >= 75 -> "Alto riesgo"
        score >= 50 -> "Moderado"
        score >= 25 -> "Bajo"
        else        -> "Óptimo"
    }
    val anim = rememberInfiniteTransition(label = "risk")
    val sweep by animateFloatAsState(score / 100f * 280f, tween(1200, easing = EaseOutCubic), label = "sw")

    GlassCard(modifier = Modifier.fillMaxWidth(), accent = scoreColor) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Score de riesgo térmico", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Arc gauge
                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val stroke = 10f
                        val startAngle = 130f
                        // Track
                        drawArc(
                            color = Color.White.copy(alpha = 0.07f),
                            startAngle = startAngle, sweepAngle = 280f,
                            useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke)
                        )
                        // Value
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(scoreColor.copy(alpha = 0.6f), scoreColor),
                                center = Offset(size.width / 2, size.height / 2)
                            ),
                            startAngle = startAngle, sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$score", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = scoreColor)
                        Text("/100", fontSize = 9.sp, color = TG.textDim)
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = scoreColor.copy(alpha = 0.15f)) {
                        Text(scoreLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scoreColor)
                    }
                    StatRow("Baseline", "${profile.baselineTemp.roundToInt()}°C", TG.textSec)
                    StatRow("Sesiones hoy", "${profile.heatSessionsToday}", TG.textSec)
                    StatRow("Cooldown promedio", "${profile.avgCooldownMinutes.roundToInt()} min", TG.textSec)
                    if (profile.isAnomaly) {
                        Surface(shape = RoundedCornerShape(6.dp), color = TG.red.copy(alpha = 0.15f)) {
                            Text("⚠ Anomalía detectada",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp, color = TG.red)
                        }
                    }
                }
            }

            // Factores del score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("Temp", profile.baselineTemp / 55f, accent),
                    Triple("CPU", profile.highCpuHeatPct, TG.amber),
                    Triple("Carga", profile.chargingHeatPct, Color(0xFFFF6D00)),
                    Triple("Racha", (profile.consecutiveHotReadings / 10f).coerceIn(0f, 1f), TG.red)
                ).forEach { (label, pct, color) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(pct.coerceIn(0f, 1f))
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.7f))),
                                        RoundedCornerShape(6.dp)
                                    )
                            )
                        }
                        Text(label, fontSize = 9.sp, color = TG.textDim, letterSpacing = 0.3.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TEMP HISTORY CHART
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TempHistoryChart(history: List<ThermalSnapshot>, accent: Color) {
    val recent = history.takeLast(40)
    val minT = (recent.minOfOrNull { it.batteryTemp } ?: 30f) - 2f
    val maxT = (recent.maxOfOrNull { it.batteryTemp } ?: 50f) + 2f

    GlassCard(modifier = Modifier.fillMaxWidth(), accent = accent) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ShowChart, null, tint = accent, modifier = Modifier.size(16.dp))
                    Text("Historial de temperatura", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                }
                Text("${recent.size} lecturas", fontSize = 10.sp, color = TG.textDim)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val w = size.width
                val h = size.height
                val n = recent.size
                if (n < 2) return@Canvas

                val pts = recent.mapIndexed { i, snap ->
                    val x = w * i / (n - 1)
                    val y = h - (h * (snap.batteryTemp - minT) / (maxT - minT)).coerceIn(0f, h)
                    Offset(x, y)
                }

                // Area fill
                val path = Path().apply {
                    moveTo(pts.first().x, h)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, h)
                    close()
                }
                drawPath(path, Brush.verticalGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent)))

                // Line
                val linePath = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val cx = (pts[i-1].x + pts[i].x) / 2
                        cubicTo(cx, pts[i-1].y, cx, pts[i].y, pts[i].x, pts[i].y)
                    }
                }
                drawPath(linePath, accent, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Último punto destacado
                drawCircle(accent, 5f, pts.last())
                drawCircle(Color.White, 2.5f, pts.last())

                // Líneas de referencia
                listOf(40f, 45f, 50f).forEach { ref ->
                    if (ref in minT..maxT) {
                        val y = h - (h * (ref - minT) / (maxT - minT))
                        drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(w, y), 1f)
                    }
                }
            }

            // Eje Y etiquetas
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${minT.roundToInt()}°", fontSize = 9.sp, color = TG.textDim)
                Text("${((minT + maxT) / 2).roundToInt()}°", fontSize = 9.sp, color = TG.textDim)
                Text("${maxT.roundToInt()}°", fontSize = 9.sp, color = TG.textDim)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  BATTERY HEALTH
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BatteryHealthCard(health: BatteryHealthScore) {
    val color = when {
        health.score >= 80 -> TG.green
        health.score >= 60 -> TG.amber
        else               -> TG.red
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), accent = color) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FavoriteBorder, null, tint = color, modifier = Modifier.size(16.dp))
                Text("Salud de la batería", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                Spacer(Modifier.weight(1f))
                Text("${health.score}/100", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            }

            // Barra de salud
            val animPct by animateFloatAsState(health.score / 100f, tween(1000, easing = EaseOutCubic), label = "hp")
            Box(modifier = Modifier.fillMaxWidth().height(6.dp)
                .background(Color.White.copy(alpha = 0.07f), CircleShape)) {
                Box(modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animPct)
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color)), CircleShape))
            }

            Text(health.level, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TG.textPri)

            if (health.factors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    health.factors.take(4).forEach { factor ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color.copy(alpha = 0.6f)))
                            Text(factor, fontSize = 11.sp, color = TG.textSec, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HOURLY HEATMAP
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HourlyHeatmapCard(hourly: List<HourlyDataPoint>, accent: Color) {
    val maxT = hourly.maxOfOrNull { it.avgTemp } ?: 45f
    val minT = hourly.minOfOrNull { it.avgTemp } ?: 30f

    GlassCard(modifier = Modifier.fillMaxWidth(), accent = accent) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AccessTime, null, tint = accent, modifier = Modifier.size(16.dp))
                Text("Mapa de calor por hora", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                hourly.sortedBy { it.hour }.forEach { dp ->
                    val pct = if (maxT > minT) ((dp.avgTemp - minT) / (maxT - minT)).coerceIn(0f, 1f) else 0.5f
                    val barColor = when {
                        dp.avgTemp >= 45f -> TG.red
                        dp.avgTemp >= 40f -> Color(0xFFFF6D00)
                        dp.avgTemp >= 37f -> TG.amber
                        else              -> TG.green
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("${dp.avgTemp.roundToInt()}°", fontSize = 7.sp, color = barColor)
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height((8 + pct * 48).dp)
                                .background(
                                    Brush.verticalGradient(listOf(barColor.copy(alpha = 0.4f), barColor)),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                        Text(
                            if (dp.hour < 10) "0${dp.hour}" else "${dp.hour}",
                            fontSize = 7.sp, color = TG.textDim
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LEARNING PROFILE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun LearningProfileCard(profile: LearnedProfile, sampleCount: Int) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Psychology, null, tint = TG.purple, modifier = Modifier.size(16.dp))
                Text("Perfil aprendido", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = TG.purple.copy(alpha = 0.15f)) {
                    Text("$sampleCount muestras",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp, color = TG.purple)
                }
            }

            val rows = listOf(
                Triple("Temp baseline", "${profile.baselineTemp.roundToInt()}°C", "Tu temperatura normal en reposo"),
                Triple("Umbral dinámico", "${profile.dynamicThreshold.roundToInt()}°C", "Cuando el motor interviene"),
                Triple("App más caliente", profile.topHeatApp.ifEmpty { "Sin datos" }, "La que más temperatura genera"),
                Triple("Causa principal", profile.learnedCause.name, "Patrón detectado"),
                Triple("Nivel de riesgo", profile.riskLevel.name, "Evaluación general"),
            )

            rows.forEach { (label, value, desc) ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TG.textPri)
                        Text(desc, fontSize = 10.sp, color = TG.textDim)
                    }
                    Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TG.purple)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HELPERS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StatRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = TG.textDim)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
