package com.jeissonalberto.thermaguard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.ThermalUiState
import kotlin.math.roundToInt

@Composable
fun StatsScreen(uiState: ThermalUiState, onResetLearning: () -> Unit) {
    val context = LocalContext.current
    val scroll  = rememberScrollState()
    val level   = uiState.latest.batteryTemp.toThermalLevel()
    val accent  = TG.accentFor(level)
    val profile = uiState.profile

    Box(modifier = Modifier.fillMaxSize().background(TG.bg)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Estadísticas", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = TG.textPri, letterSpacing = (-0.5).sp)
                    Text("Análisis del motor v4", fontSize = 11.sp, color = TG.textSec)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = {
                        try {
                            // Exportar CSV
                            val sb = StringBuilder()
                            sb.appendLine("timestamp,temp,cpu,bateria,app,nivel")
                            uiState.history.forEach { s ->
                                val dt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(s.timestamp))
                                sb.appendLine("$dt,${s.batteryTemp},${s.cpuUsage.toInt()},${s.batteryLevel},${s.topApp},${s.batteryTemp.toThermalLevel().name}")
                            }
                            val file = java.io.File(context.getExternalFilesDir(null), "thermaguard_${System.currentTimeMillis()}.csv")
                            file.writeText(sb.toString())
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            context.startActivity(Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        } catch (_: Exception) {}
                    }) {
                        Icon(Icons.Default.Download, null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onResetLearning) {
                        Icon(Icons.Default.Refresh, null, tint = TG.textDim, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Risk Score card
            profile?.let { RiskScoreCard(profile = it, accent = accent) }

            // Gráfico 24h
            if (uiState.history.size >= 3) {
                TempChart24h(history = uiState.history, accent = accent)
            }

            // Ranking apps
            if (uiState.appHeatRanking.isNotEmpty()) {
                AppRankingCard(ranking = uiState.appHeatRanking)
            }

            // Perfil horario
            if (uiState.hourlyProfile.isNotEmpty()) {
                HourlyProfileCard(hourlyProfile = uiState.hourlyProfile, accent = accent)
            }

            // Batería
            uiState.batteryHealth?.let { BatteryHealthCard(health = it) }

            // Sesiones de calor
            profile?.let { p ->
                if (p.heatSessionsToday > 0) {
                    HeatSessionsCard(profile = p, accent = accent)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Gráfico temperatura 24h ────────────────────────────────────────────────
@Composable
fun TempChart24h(history: List<ThermalSnapshot>, accent: Color) {
    // Filtrar últimas 24h
    val cutoff = System.currentTimeMillis() - 24 * 3600 * 1000L
    val data   = history.filter { it.timestamp >= cutoff }.takeLast(50)
    if (data.size < 3) return

    val minTemp = data.minOf { it.batteryTemp }.coerceAtMost(30f)
    val maxTemp = data.maxOf { it.batteryTemp }.coerceAtLeast(minTemp + 5f)
    val range   = maxTemp - minTemp

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Timeline, null, tint = accent, modifier = Modifier.size(16.dp))
                Text("Temperatura últimas 24h", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = TG.textPri)
                Spacer(Modifier.weight(1f))
                Text("${data.last().batteryTemp}°C ahora",
                    fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
            }

            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val w = size.width; val h = size.height
                val step = if (data.size > 1) w / (data.size - 1) else w

                // Líneas de referencia
                listOf(35f, 40f, 45f).forEach { refTemp ->
                    val y = h - ((refTemp - minTemp) / range * h)
                    drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }

                // Área rellena bajo la curva
                val path = Path()
                data.forEachIndexed { i, s ->
                    val x = i * step
                    val y = h - ((s.batteryTemp - minTemp) / range * h)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.lineTo((data.size - 1) * step, h)
                path.lineTo(0f, h)
                path.close()
                drawPath(path, Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.25f), accent.copy(alpha = 0.02f))
                ))

                // Línea principal
                val linePath = Path()
                data.forEachIndexed { i, s ->
                    val x = i * step
                    val y = h - ((s.batteryTemp - minTemp) / range * h)
                    if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }
                drawPath(linePath, accent, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Punto actual
                val lastX = (data.size - 1) * step
                val lastY = h - ((data.last().batteryTemp - minTemp) / range * h)
                drawCircle(accent, 5f, Offset(lastX, lastY))
                drawCircle(accent.copy(alpha = 0.3f), 10f, Offset(lastX, lastY))
            }

            // Eje Y labels
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${minTemp.toInt()}°", fontSize = 9.sp, color = TG.textDim)
                Text("${((minTemp + maxTemp) / 2).toInt()}°", fontSize = 9.sp, color = TG.textDim)
                Text("${maxTemp.toInt()}°", fontSize = 9.sp, color = TG.textDim)
            }
        }
    }
}

// ── Ranking de apps ────────────────────────────────────────────────────────
@Composable
fun AppRankingCard(ranking: List<Pair<String, Float>>) {
    if (ranking.isEmpty()) return
    val maxScore = ranking.first().second.coerceAtLeast(35f)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Whatshot, null, tint = TG.amber, modifier = Modifier.size(16.dp))
                Text("Apps que más calientan", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = TG.textPri)
            }
            ranking.forEachIndexed { idx, (app, avgTemp) ->
                val barColor = when {
                    avgTemp >= 44f -> TG.red
                    avgTemp >= 40f -> TG.amber
                    else           -> TG.teal
                }
                val pct = ((avgTemp - 30f) / (maxScore - 30f)).coerceIn(0f, 1f)
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${idx + 1}", fontSize = 11.sp, color = TG.textDim,
                        modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(app, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TG.textPri)
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.08f))) {
                            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct)
                                .clip(RoundedCornerShape(2.dp)).background(barColor))
                        }
                    }
                    Text("${avgTemp.toInt()}°C", fontSize = 11.sp,
                        color = barColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Perfil horario ──────────────────────────────────────────────────────────
@Composable
fun HourlyProfileCard(hourlyProfile: List<HourlyDataPoint>, accent: Color) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, null, tint = accent, modifier = Modifier.size(16.dp))
                Text("Perfil horario aprendido", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = TG.textPri)
            }
            val validHours = hourlyProfile.filter { it.avgTemp > 0f }
            if (validHours.isEmpty()) {
                Text("Recopilando datos horarios...", fontSize = 12.sp, color = TG.textSec)
            } else {
                val maxT = validHours.maxOf { it.avgTemp }
                val minT = validHours.minOf { it.avgTemp }
                Row(modifier = Modifier.fillMaxWidth().height(60.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    validHours.forEach { h ->
                        val pct = if (maxT > minT) (h.avgTemp - minT) / (maxT - minT) else 0.5f
                        val c   = when {
                            h.avgTemp >= 44f -> TG.red
                            h.avgTemp >= 40f -> TG.amber
                            else             -> accent
                        }
                        Column(modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom) {
                            Box(modifier = Modifier.fillMaxWidth()
                                .height((pct * 50f).coerceAtLeast(4f).dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(c.copy(alpha = 0.7f)))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("00h", fontSize = 8.sp, color = TG.textDim)
                    Text("12h", fontSize = 8.sp, color = TG.textDim)
                    Text("23h", fontSize = 8.sp, color = TG.textDim)
                }
            }
        }
    }
}

// ── Sesiones de calor ───────────────────────────────────────────────────────
@Composable
fun HeatSessionsCard(profile: LearnedProfile, accent: Color) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = TG.red, modifier = Modifier.size(16.dp))
                Text("Sesiones de calor hoy", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = TG.textPri)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChip("Sesiones", "${profile.heatSessionsToday}", TG.red)
                StatChip("Cooldown prom.", "${profile.avgCooldownMinutes.toInt()} min", TG.teal)
                StatChip("Máx registrado", "${profile.maxRecordedTemp.toInt()}°C", TG.amber)
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 9.sp, color = TG.textSec, textAlign = TextAlign.Center)
    }
}

// ── Risk Score card (ya existía, la redeclaro aquí limpia) ────────────────
@Composable
fun RiskScoreCard(profile: LearnedProfile, accent: Color) {
    val score = profile.riskScore.coerceIn(0, 100)
    val scoreColor = when {
        score >= 70 -> TG.red
        score >= 40 -> TG.amber
        else        -> TG.green
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), accent = scoreColor) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(Color.White.copy(alpha = 0.08f), -220f, 260f, false,
                        style = Stroke(6f, cap = StrokeCap.Round))
                    drawArc(scoreColor, -220f, 260f * score / 100f, false,
                        style = Stroke(6f, cap = StrokeCap.Round))
                }
                Text("$score", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = scoreColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Risk Score", fontSize = 10.sp, color = TG.textSec, letterSpacing = 0.5.sp)
                Text(when {
                    score >= 70 -> "Nivel alto — tomar acción"
                    score >= 40 -> "Moderado — monitorear"
                    else        -> "Normal — todo bien"
                }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                Spacer(Modifier.height(4.dp))
                Text("${profile.samplesCollected} lecturas · umbral ${profile.dynamicThreshold.toInt()}°C",
                    fontSize = 10.sp, color = TG.textDim)
            }
        }
    }
}

// ── Battery health card ───────────────────────────────────────────────────
@Composable
fun BatteryHealthCard(health: BatteryHealthScore) {
    val c = when {
        health.score < 40 -> TG.red
        health.score < 70 -> TG.amber
        else              -> TG.green
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), accent = c) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.BatteryFull, null, tint = c, modifier = Modifier.size(16.dp))
                Text("Salud de la batería", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = TG.textPri)
                Spacer(Modifier.weight(1f))
                Text("${health.score}/100", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c)
            }
            Box(modifier = Modifier.fillMaxWidth().height(5.dp)
                .clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.08f))) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(health.score / 100f)
                    .clip(RoundedCornerShape(3.dp)).background(c))
            }
            health.tips.take(2).forEach { tip ->
                Text("• $tip", fontSize = 11.sp, color = TG.textSec, lineHeight = 15.sp)
            }
        }
    }
}
