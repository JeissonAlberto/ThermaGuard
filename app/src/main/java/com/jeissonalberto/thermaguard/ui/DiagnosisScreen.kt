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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.ThermalUiState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DiagnosisScreen(uiState: ThermalUiState) {
    val scroll = rememberScrollState()
    val snap   = uiState.latest
    val diags  = uiState.componentDiagnoses
    val level  = snap.batteryTemp.toThermalLevel()
    val accent = TG.accentFor(level)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(
                colors  = listOf(TG.glowFor(level).copy(alpha = 0.12f), TG.bg),
                center  = Offset(1f, 0f),
                radius  = 800f
            ))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Science, null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Diagnóstico", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = TG.textPri, letterSpacing = (-0.5).sp)
                    Text("Sensores del chip en tiempo real · Motor v5", fontSize = 12.sp, color = TG.textSec)
                }
            }

            // Radar visual de componentes
            if (diags.isNotEmpty()) {
                ComponentRadar(diags = diags, accent = accent)
            }

            // Cards individuales por componente
            if (diags.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = accent, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                            Text("Escaneando componentes…", fontSize = 13.sp, color = TG.textSec)
                        }
                    }
                }
            } else {
                diags.forEach { diag ->
                    ComponentCard(diag = diag)
                }
            }

            // App activa con más carga
            if (snap.topApp.isNotEmpty() && snap.cpuUsage > 30f) {
                TopAppCard(appName = snap.topApp, cpuPct = snap.cpuUsage)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  COMPONENT RADAR (Canvas)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ComponentRadar(diags: List<ComponentDiagnosis>, accent: Color) {
    GlassCard(modifier = Modifier.fillMaxWidth(), accent = accent) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mapa de calor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)

            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                val radarColor = accent
                androidx.compose.foundation.Canvas(modifier = Modifier.size(180.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension / 2f
                    val n = diags.size.coerceAtLeast(1)

                    // Anillos de referencia
                    for (ring in 1..3) {
                        val r = maxR * ring / 3f
                        drawCircle(
                            color  = Color.White.copy(alpha = 0.06f),
                            radius = r, center = Offset(cx, cy),
                            style  = Stroke(1f)
                        )
                    }

                    // Radar area
                    val pts = diags.mapIndexed { i, diag ->
                        val angle = (2 * Math.PI * i / n - Math.PI / 2).toFloat()
                        val pct   = (diag.temp / 60f).coerceIn(0f, 1f)
                        val r     = maxR * pct
                        Offset(cx + r * cos(angle), cy + r * sin(angle))
                    }

                    if (pts.size >= 3) {
                        val path = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                        drawPath(path, radarColor.copy(alpha = 0.2f))
                        drawPath(path, radarColor.copy(alpha = 0.6f), style = Stroke(2f))
                    }

                    // Puntos
                    pts.forEach { pt ->
                        drawCircle(radarColor, 5f, pt)
                        drawCircle(Color.White, 2f, pt)
                    }

                    // Líneas del eje
                    diags.forEachIndexed { i, _ ->
                        val angle = (2 * Math.PI * i / n - Math.PI / 2).toFloat()
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(cx, cy),
                            end   = Offset(cx + maxR * cos(angle), cy + maxR * sin(angle)),
                            strokeWidth = 1f
                        )
                    }
                }

                // Etiquetas
                diags.forEachIndexed { i, diag ->
                    val angle = (2 * Math.PI * i / diags.size - Math.PI / 2).toFloat()
                    val labelR = 105f
                    val x = (90 + labelR * cos(angle)).dp
                    val y = (90 + labelR * sin(angle)).dp
                    Box(modifier = Modifier.offset(x = x - 20.dp, y = y - 8.dp)) {
                        Text(diag.component.icon, fontSize = 14.sp)
                    }
                }
            }

            // Leyenda
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                diags.take(4).forEach { diag ->
                    val c = componentColor(diag.status)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(c))
                        Text("${diag.component.label.take(4)} ${diag.temp.toInt()}°",
                            fontSize = 9.sp, color = TG.textSec)
                    }
                }
            }
        }
    }
}

fun componentColor(status: ComponentStatus) = when (status) {
    ComponentStatus.NORMAL   -> TG.green
    ComponentStatus.WARM     -> TG.amber
    ComponentStatus.HOT      -> Color(0xFFFF6D00)
    ComponentStatus.CRITICAL -> TG.red
}

// ─────────────────────────────────────────────────────────────────────────────
//  COMPONENT CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ComponentCard(diag: ComponentDiagnosis) {
    val color = componentColor(diag.status)
    GlassCard(modifier = Modifier.fillMaxWidth(), accent = color, cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(diag.component.icon, fontSize = 18.sp)
                    }
                    Column {
                        Text(diag.component.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                        Text(diag.status.label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = color, letterSpacing = 1.5.sp)
                    }
                }
                Text("${diag.temp.toInt()}°C", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
            }

            // Barra de temperatura
            val pct = (diag.temp / 65f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier.fillMaxWidth().height(5.dp)
                    .background(Color.White.copy(alpha = 0.06f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(pct)
                        .background(
                            Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color)),
                            CircleShape
                        )
                )
            }

            if (diag.cause.isNotEmpty()) {
                Text(diag.cause, fontSize = 11.sp, color = TG.textSec, lineHeight = 15.sp)
            }
            if (diag.advice.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Lightbulb, null, tint = TG.amber, modifier = Modifier.size(13.dp).padding(top = 1.dp))
                    Text(diag.advice, fontSize = 11.sp, color = TG.textSec, lineHeight = 15.sp)
                }
            }

            // Per-core si es CPU
            if (diag.perCore.isNotEmpty()) {
                Text("Núcleos", fontSize = 10.sp, color = TG.textDim, letterSpacing = 0.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    diag.perCore.take(8).forEachIndexed { i, v ->
                        val c2 = when {
                            v > 75 -> TG.red
                            v > 45 -> TG.amber
                            else   -> TG.green
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${v.toInt()}%", fontSize = 8.sp, color = c2)
                            Box(
                                modifier = Modifier
                                    .width(14.dp).height(30.dp)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(v / 100f)
                                        .align(Alignment.BottomCenter)
                                        .background(c2.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TOP APP CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TopAppCard(appName: String, cpuPct: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TG.amber.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .border(1.dp, TG.amber.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🔥", fontSize = 22.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text("App más activa", fontSize = 10.sp, color = TG.textSec, letterSpacing = 0.3.sp)
            Text(appName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
        }
        Surface(shape = RoundedCornerShape(8.dp), color = TG.amber.copy(alpha = 0.15f)) {
            Text("CPU ${cpuPct.toInt()}%",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TG.amber)
        }
    }
}
