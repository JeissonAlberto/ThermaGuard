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

@Composable
fun DiagnosisScreen(uiState: ThermalUiState) {
    val scroll = rememberScrollState()
    val snap   = uiState.latest
    val diags  = uiState.componentDiagnoses
    val level  = snap.batteryTemp.toThermalLevel()
    val accent = TG.accentFor(level)

    Box(modifier = Modifier.fillMaxSize().background(TG.bg)) {
        // Orb radial
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
                // Temp grande en badge
                Box(modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.1f))
                    .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("${snap.batteryTemp.toInt()}°C",
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = accent)
                }
            }

            // Radar visual
            if (diags.isNotEmpty()) ComponentRadar(diags = diags, accent = accent)

            // Estado loading
            if (diags.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)).background(TG.glass)
                    .border(1.dp, TG.glassBorder, RoundedCornerShape(20.dp))
                    .padding(40.dp),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                        Text("Escaneando componentes…", fontSize = 13.sp, color = TG.textSec)
                    }
                }
            } else {
                // Grid 2x2 para los componentes principales
                val mainDiags = diags.take(4)
                val restDiags = diags.drop(4)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    mainDiags.chunked(2).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { diag ->
                                ComponentCardCompact(diag = diag, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    // Resto en lista
                    restDiags.forEach { diag -> ComponentCard(diag = diag) }
                }
            }

            // App activa
            if (snap.topApp.isNotEmpty() && snap.cpuUsage > 30f)
                TopAppCard(appName = snap.topApp, cpuPct = snap.cpuUsage)

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  COMPONENT RADAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ComponentRadar(diags: List<ComponentDiagnosis>, accent: Color) {
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
            Icon(Icons.Default.Radar, null, tint = accent, modifier = Modifier.size(16.dp))
            Text("Mapa de calor", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = TG.textPri)
            Spacer(Modifier.weight(1f))
            // Leyenda
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendDot(TG.green, "OK")
                LegendDot(TG.amber, "Cálido")
                LegendDot(TG.red, "Caliente")
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(210.dp),
            contentAlignment = Alignment.Center) {
            val inf = rememberInfiniteTransition(label = "radar")
            val rot by inf.animateFloat(0f, 360f,
                infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "r")

            androidx.compose.foundation.Canvas(modifier = Modifier.size(185.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = size.minDimension / 2f * 0.88f
                val n = diags.size.coerceAtLeast(3)

                // Línea de barrido (radar scan)
                val scanRad = Math.toRadians(rot.toDouble())
                drawLine(accent.copy(alpha = 0.3f),
                    Offset(cx, cy),
                    Offset(cx + (maxR * cos(scanRad)).toFloat(), cy + (maxR * sin(scanRad)).toFloat()),
                    strokeWidth = 1.5f)
                // Halo de barrido
                drawArc(accent.copy(alpha = 0.06f), rot - 40f, 40f, true,
                    Offset(cx - maxR, cy - maxR), Size(maxR * 2, maxR * 2))

                // Anillos de referencia
                for (ring in 1..3) {
                    val r = maxR * ring / 3f
                    drawCircle(Color.White.copy(alpha = if (ring == 3) 0.12f else 0.05f),
                        radius = r, center = Offset(cx, cy), style = Stroke(1.2f))
                }

                // Puntos de componente
                diags.forEachIndexed { i, d ->
                    val angle = (2 * PI / n * i) - PI / 2
                    val norm  = (d.score / 100f).coerceIn(0f, 1f)
                    val r     = maxR * (0.25f + norm * 0.75f)
                    val dx = cx + (r * cos(angle)).toFloat()
                    val dy = cy + (r * sin(angle)).toFloat()
                    val dotC = when {
                        d.score >= 70 -> android.graphics.Color.parseColor("#FF5252")
                        d.score >= 40 -> android.graphics.Color.parseColor("#FFD740")
                        else          -> android.graphics.Color.parseColor("#00E676")
                    }
                    // Línea al centro
                    drawLine(Color(dotC).copy(alpha = 0.2f), Offset(cx, cy), Offset(dx, dy), strokeWidth = 1f)
                    // Halo
                    drawCircle(Color(dotC).copy(alpha = 0.25f), 11f, Offset(dx, dy))
                    // Punto
                    drawCircle(Color(dotC), 5.5f, Offset(dx, dy))
                }
            }

            // Labels fuera del canvas
            diags.forEachIndexed { i, d ->
                val n     = diags.size.coerceAtLeast(3)
                val angle = (2 * PI / n * i) - PI / 2
                val r     = 98f
                val dx = (r * cos(angle)).dp
                val dy = (r * sin(angle)).dp
                Column(modifier = Modifier.offset(dx, dy),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.component, fontSize = 7.sp, color = TG.textSec, textAlign = TextAlign.Center)
                    Text("${d.temp.toInt()}°", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        color = when {
                            d.score >= 70 -> TG.red; d.score >= 40 -> TG.amber; else -> TG.green })
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

// ─────────────────────────────────────────────────────────────────────────────
//  COMPONENT CARD COMPACTO (grid 2x2)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ComponentCardCompact(diag: ComponentDiagnosis, modifier: Modifier = Modifier) {
    val tempColor = when {
        diag.score >= 70 -> TG.red
        diag.score >= 40 -> TG.amber
        else             -> TG.green
    }
    val scoreAnim by animateFloatAsState(diag.score / 100f, tween(800, easing = EaseOutCubic), label = "sc")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TG.glass)
            .border(1.dp, tempColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(tempColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center) {
                Icon(componentIcon(diag.component), null, tint = tempColor, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(diag.component, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TG.textPri)
                Text(diag.status, fontSize = 9.sp, color = tempColor)
            }
        }
        // Temp grande
        Text("${diag.temp.toInt()}°C", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            color = tempColor)
        // Mini barra de score
        Box(modifier = Modifier.fillMaxWidth().height(4.dp)
            .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.07f))) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(scoreAnim)
                .clip(RoundedCornerShape(2.dp)).background(tempColor))
        }
        if (diag.recommendation.isNotEmpty())
            Text(diag.recommendation, fontSize = 9.sp, color = TG.textSec, lineHeight = 12.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  COMPONENT CARD (lista)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ComponentCard(diag: ComponentDiagnosis) {
    val tempColor = when {
        diag.score >= 70 -> TG.red
        diag.score >= 40 -> TG.amber
        else             -> TG.green
    }
    val scoreAnim by animateFloatAsState(diag.score / 100f, tween(800, easing = EaseOutCubic), label = "sca")
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TG.glass)
            .border(1.dp, tempColor.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
            .background(tempColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center) {
            Icon(componentIcon(diag.component), null, tint = tempColor, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(diag.component, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                Text("${diag.temp.toInt()}°C", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = tempColor)
            }
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.07f))) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(scoreAnim)
                    .clip(RoundedCornerShape(2.dp)).background(tempColor))
            }
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(diag.status, fontSize = 10.sp, color = tempColor.copy(alpha = 0.8f))
                Text("Score ${diag.score}", fontSize = 9.sp, color = TG.textDim)
            }
            if (diag.recommendation.isNotEmpty())
                Text(diag.recommendation, fontSize = 10.sp, color = TG.textSec, lineHeight = 13.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TOP APP CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TopAppCard(appName: String, cpuPct: Float) {
    val c = when {
        cpuPct > 75f -> TG.red
        cpuPct > 45f -> TG.amber
        else         -> TG.teal
    }
    val barAnim by animateFloatAsState(cpuPct / 100f, tween(700, easing = EaseOutCubic), label = "b")
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TG.glass)
            .border(1.dp, c.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
            .background(c.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Apps, null, tint = c, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("App más activa ahora", fontSize = 10.sp, color = TG.textSec, letterSpacing = 0.3.sp)
                    Text(appName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                }
                Text("${cpuPct.toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c)
            }
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.07f))) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(barAnim)
                    .clip(RoundedCornerShape(2.dp)).background(
                        Brush.horizontalGradient(listOf(TG.green, TG.amber, TG.red))))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HELPERS
// ─────────────────────────────────────────────────────────────────────────────
private fun componentIcon(name: String) = when {
    name.contains("CPU", ignoreCase = true)     -> Icons.Default.Memory
    name.contains("GPU", ignoreCase = true)     -> Icons.Default.ViewInAr
    name.contains("Bat", ignoreCase = true)     -> Icons.Default.BatteryFull
    name.contains("Piel", ignoreCase = true) ||
    name.contains("Skin", ignoreCase = true)    -> Icons.Default.Smartphone
    name.contains("Cam", ignoreCase = true)     -> Icons.Default.CameraAlt
    name.contains("Modem", ignoreCase = true) ||
    name.contains("Radio", ignoreCase = true)   -> Icons.Default.SignalCellularAlt
    name.contains("Pantalla", ignoreCase = true)||
    name.contains("Display", ignoreCase = true) -> Icons.Default.Tv
    name.contains("Board", ignoreCase = true)   -> Icons.Default.DeveloperBoard
    else                                        -> Icons.Default.Thermostat
}
