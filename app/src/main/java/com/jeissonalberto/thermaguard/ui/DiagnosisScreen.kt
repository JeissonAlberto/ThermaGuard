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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

// Nivel de calor de ComponentDiagnosis (basado en ComponentStatus)
private val ComponentDiagnosis.heatColor: Color get() = when (status) {
    ComponentStatus.CRITICAL -> TG.red
    ComponentStatus.HOT      -> TG.amber
    ComponentStatus.WARM     -> Color(0xFFFFD740)
    else                     -> TG.green
}
private val ComponentDiagnosis.heatScore: Float get() {
    // La barra refleja la temperatura real, no un valor fijo por status
    val (minT, maxT) = when (component) {
        ThermalComponent.CPU     -> Pair(30f, 82f)
        ThermalComponent.GPU     -> Pair(28f, 65f)
        ThermalComponent.DISPLAY -> Pair(25f, 62f)
        ThermalComponent.MODEM   -> Pair(25f, 60f)
        ThermalComponent.BATTERY -> Pair(20f, 52f)
        else                     -> Pair(25f, 65f)
    }
    return ((temp - minT) / (maxT - minT)).coerceIn(0.05f, 1f)
}

@Composable
fun DiagnosisScreen(uiState: ThermalUiState) {
    val scroll = rememberScrollState()
    val snap   = uiState.latest
    val diags  = uiState.componentDiagnoses
    val mainTemp = if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp
    val level  = mainTemp.toThermalLevel()
    val accent = TG.accentFor(level)

    Box(modifier = Modifier.fillMaxSize().background(TG.bg)) {
        Box(modifier = Modifier
            .size(350.dp).align(Alignment.TopCenter).offset(y = (-60).dp).blur(100.dp)
            .background(TG.glowFor(level).copy(alpha = 0.35f), CircleShape))

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Science, null, tint = accent, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text("Diagnóstico", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                            color = TG.textPri, letterSpacing = (-0.8).sp)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(accent))
                            Text("Sensores  ·  Motor v5", fontSize = 10.sp,
                                color = TG.textDim, letterSpacing = 0.3.sp)
                        }
                    }
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.1f))
                    .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("${snap.batteryTemp.toInt()}°C",
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = accent)
                }
            }

            // Radar
            if (diags.isNotEmpty()) ComponentRadar(diags = diags, accent = accent)

            // Loading
            if (diags.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)).background(TG.glass)
                    .border(1.dp, TG.glassBorder, RoundedCornerShape(20.dp))
                    .padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                        Text("Escaneando componentes…", fontSize = 13.sp, color = TG.textSec)
                    }
                }
            } else {
                // Grid 2x2 primeros 4
                val main = diags.take(4)
                val rest = diags.drop(4)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    main.chunked(2).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { diag ->
                                ComponentCardCompact(diag = diag, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    rest.forEach { diag -> ComponentCard(diag = diag) }
                }
            }

            if (snap.topApp.isNotEmpty() && snap.cpuUsage > 30f)
                TopAppCard(appName = snap.topApp, cpuPct = snap.cpuUsage)

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ─── RADAR ────────────────────────────────────────────────────────────────────
@Composable
fun ComponentRadar(diags: List<ComponentDiagnosis>, accent: Color) {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(20.dp)).background(TG.glass)
        .border(1.dp, TG.glassBorder, RoundedCornerShape(20.dp))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Radar, null, tint = accent, modifier = Modifier.size(16.dp))
            Text("Mapa de calor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendDot(TG.green, "OK")
                LegendDot(TG.amber, "Cálido")
                LegendDot(TG.red, "Caliente")
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            val inf = rememberInfiniteTransition(label = "radar")
            val rot by inf.animateFloat(0f, 360f,
                infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "r")

            androidx.compose.foundation.Canvas(modifier = Modifier.size(180.dp)) {
                val cx = size.width / 2f; val cy = size.height / 2f
                val maxR = size.minDimension / 2f * 0.85f
                val n = diags.size.coerceAtLeast(3)

                // Barrido radar
                val scanRad = Math.toRadians(rot.toDouble())
                drawLine(accent.copy(alpha = 0.28f), Offset(cx, cy),
                    Offset(cx + (maxR * cos(scanRad)).toFloat(), cy + (maxR * sin(scanRad)).toFloat()), 1.5f)
                drawArc(accent.copy(alpha = 0.05f), rot - 45f, 45f, true,
                    Offset(cx - maxR, cy - maxR), Size(maxR * 2, maxR * 2))

                // Anillos
                for (ring in 1..3) {
                    drawCircle(Color.White.copy(alpha = if (ring == 3) 0.10f else 0.04f),
                        maxR * ring / 3f, Offset(cx, cy), style = Stroke(1.2f))
                }

                // Puntos de componentes
                diags.forEachIndexed { i, d ->
                    val ang  = (2 * PI / n * i) - PI / 2
                    val norm = d.heatScore
                    val r    = maxR * (0.22f + norm * 0.78f)
                    val dx = cx + (r * cos(ang)).toFloat()
                    val dy = cy + (r * sin(ang)).toFloat()
                    val dotC = d.heatColor
                    drawLine(dotC.copy(alpha = 0.18f), Offset(cx, cy), Offset(dx, dy), 1f)
                    drawCircle(dotC.copy(alpha = 0.22f), 10f, Offset(dx, dy))
                    drawCircle(dotC, 5f, Offset(dx, dy))
                }
            }

            // Labels
            diags.forEachIndexed { i, d ->
                val n = diags.size.coerceAtLeast(3)
                val ang = (2 * PI / n * i) - PI / 2
                val r = 96f
                val dx = (r * cos(ang)).dp
                val dy = (r * sin(ang)).dp
                Column(modifier = Modifier.offset(dx, dy),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.component.label, fontSize = 7.sp, color = TG.textSec, textAlign = TextAlign.Center)
                    Text("${d.temp.toInt()}°", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = d.heatColor)
                }
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 8.sp, color = TG.textSec)
    }
}

// ─── CARD COMPACTO (grid) ────────────────────────────────────────────────────
@Composable
fun ComponentCardCompact(diag: ComponentDiagnosis, modifier: Modifier = Modifier) {
    val c = diag.heatColor
    val scoreAnim by animateFloatAsState(diag.heatScore, tween(800, easing = EaseOutCubic), label = "sc")
    Column(modifier = modifier
        .clip(RoundedCornerShape(16.dp)).background(TG.glass)
        .border(1.dp, c.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(c.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(componentIcon(diag.component), null, tint = c, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(diag.component.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TG.textPri)
                Text(diag.status.label, fontSize = 9.sp, color = c)
            }
        }
        Text("${diag.temp.toInt()}°C", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = c)
        Box(modifier = Modifier.fillMaxWidth().height(4.dp)
            .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.07f))) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(scoreAnim)
                .clip(RoundedCornerShape(2.dp)).background(c))
        }
        if (diag.advice.isNotEmpty())
            Text(diag.advice, fontSize = 9.sp, color = TG.textSec, lineHeight = 12.sp)
    }
}

// ─── CARD LISTA ──────────────────────────────────────────────────────────────
@Composable
fun ComponentCard(diag: ComponentDiagnosis) {
    val c = diag.heatColor
    val scoreAnim by animateFloatAsState(diag.heatScore, tween(800, easing = EaseOutCubic), label = "sca")
    Row(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp)).background(TG.glass)
        .border(1.dp, c.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
            .background(c.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(componentIcon(diag.component), null, tint = c, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(diag.component.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                Text("${diag.temp.toInt()}°C", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c)
            }
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.07f))) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(scoreAnim)
                    .clip(RoundedCornerShape(2.dp)).background(c))
            }
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(diag.status.label, fontSize = 10.sp, color = c.copy(alpha = 0.8f))
                if (diag.cause.isNotEmpty())
                    Text(diag.cause, fontSize = 9.sp, color = TG.textDim)
            }
            if (diag.advice.isNotEmpty())
                Text(diag.advice, fontSize = 10.sp, color = TG.textSec, lineHeight = 13.sp)
        }
    }
}

// ─── TOP APP ─────────────────────────────────────────────────────────────────
@Composable
fun TopAppCard(appName: String, cpuPct: Float) {
    val c = when { cpuPct > 75f -> TG.red; cpuPct > 45f -> TG.amber; else -> TG.teal }
    val barAnim by animateFloatAsState(cpuPct / 100f, tween(700, easing = EaseOutCubic), label = "b")
    Row(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp)).background(TG.glass)
        .border(1.dp, c.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
            .background(c.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Apps, null, tint = c, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("App más activa", fontSize = 10.sp, color = TG.textSec)
                    Text(appName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                }
                Text("${cpuPct.toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c)
            }
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.07f))) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(barAnim)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(TG.green, TG.amber, TG.red))))
            }
        }
    }
}

// ─── HELPERS ─────────────────────────────────────────────────────────────────
private fun componentIcon(comp: ThermalComponent) = when (comp) {
    ThermalComponent.CPU     -> Icons.Default.Memory
    ThermalComponent.GPU     -> Icons.Default.ViewInAr
    ThermalComponent.BATTERY -> Icons.Default.BatteryFull
    ThermalComponent.MODEM   -> Icons.Default.SignalCellularAlt
    ThermalComponent.DISPLAY -> Icons.Default.Tv
    ThermalComponent.BOARD   -> Icons.Default.DeveloperBoard
    ThermalComponent.PROCESS -> Icons.Default.Apps
}
