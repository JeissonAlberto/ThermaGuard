package com.jeissonalberto.thermaguard.ui

import android.content.Intent
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
import androidx.compose.ui.graphics.PathEffect
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
        // Orb de fondo
        Box(modifier = Modifier
            .size(300.dp).align(Alignment.TopEnd)
            .offset(x = 60.dp, y = (-40).dp).blur(90.dp)
            .background(accent.copy(alpha = 0.07f), CircleShape))

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header mejorado
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Estadísticas", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                        color = TG.textPri, letterSpacing = (-0.8).sp)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(accent))
                        Text("Motor v5  ·  Análisis de Moore", fontSize = 10.sp,
                            color = TG.textDim, letterSpacing = 0.3.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Botón exportar CSV
                    Box(
                        modifier = Modifier.size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.1f))
                            .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable {
                                try {
                                    val sb = StringBuilder()
                                    sb.appendLine("timestamp,temp,cpu,bateria,app,nivel")
                                    uiState.history.forEach { s ->
                                        val dt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                                            java.util.Locale.getDefault()).format(java.util.Date(s.timestamp))
                                        sb.appendLine("$dt,${s.batteryTemp},${s.cpuUsage.toInt()},${s.batteryLevel},${s.topApp},${s.batteryTemp.toThermalLevel().name}")
                                    }
                                    val file = java.io.File(context.getExternalFilesDir(null),
                                        "thermaguard_${System.currentTimeMillis()}.csv")
                                    file.writeText(sb.toString())
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context,
                                        "${context.packageName}.provider", file)
                                    context.startActivity(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                } catch (_: Exception) {}
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Download, null, tint = accent, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier.size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TG.glass)
                            .border(1.dp, TG.glassBorder, RoundedCornerShape(12.dp))
                            .clickable { onResetLearning() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = TG.textDim, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Resumen rápido — 3 chips
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val snap = uiState.latest
                StatMini("Temp actual", "${snap.batteryTemp.toInt()}°C", TG.accentFor(level), Modifier.weight(1f))
                StatMini("Lecturas", "${uiState.history.size}", TG.blue, Modifier.weight(1f))
                StatMini("Riesgo", "${profile?.riskScore ?: 0}%",
                    if ((profile?.riskScore ?: 0) > 50) TG.red else TG.green, Modifier.weight(1f))
            }

            // Risk Score card
            profile?.let { RiskScoreCard(profile = it, accent = accent) }

            // Gráfico 24h
            if (uiState.history.size >= 3) TempChart24h(history = uiState.history, accent = accent)

            // Ranking apps
            if (uiState.appHeatRanking.isNotEmpty()) AppRankingCard(ranking = uiState.appHeatRanking)

            // Perfil horario
            if (uiState.hourlyProfile.isNotEmpty())
                HourlyProfileCard(hourlyProfile = uiState.hourlyProfile, accent = accent)

            // Batería
            uiState.batteryHealth?.let { BatteryHealthCard(health = it) }

            // Sesiones de calor
            profile?.let { p ->
                if (p.heatSessionsToday > 0) HeatSessionsCard(profile = p, accent = accent)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun StatMini(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TG.glass)
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 9.sp, color = TG.textSec, textAlign = TextAlign.Center)
    }
}

// ── Gráfico temperatura 24h ────────────────────────────────────────────────
@Composable
fun TempChart24h(history: List<ThermalSnapshot>, accent: Color) {
    val cutoff = System.currentTimeMillis() - 24 * 3600 * 1000L
    val data   = history.filter { it.timestamp >= cutoff }.takeLast(60)
    if (data.size < 3) return
    val minTemp = data.minOf { it.batteryTemp }.coerceAtMost(30f)
    val maxTemp = data.maxOf { it.batteryTemp }.coerceAtLeast(minTemp + 5f)
    val range   = maxTemp - minTemp

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
            Icon(Icons.Default.Timeline, null, tint = accent, modifier = Modifier.size(16.dp))
            Text("Temperatura últimas 24h", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = TG.textPri)
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("${data.last().batteryTemp}°C", fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, color = accent)
            }
        }
        // Zona de temperatura de riesgo (fondo rojo sutil > 43°C)
        Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            val w = size.width; val h = size.height
            val step = if (data.size > 1) w / (data.size - 1) else w
            // Zona de riesgo (> 43°C)
            val riskY = h - ((43f - minTemp) / range * h).coerceIn(0f, h)
            drawRect(Color(0xFFFF1744).copy(alpha = 0.05f),
                topLeft = Offset(0f, riskY), size = Size(w, h - riskY))
            // Línea de riesgo
            drawLine(TG.red.copy(alpha = 0.3f), Offset(0f, riskY), Offset(w, riskY),
                strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
            // Líneas de referencia
            listOf(35f, 40f, 45f).forEach { refTemp ->
                val y = h - ((refTemp - minTemp) / range * h).coerceIn(0f, h)
                drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            // Área bajo la curva
            val path = Path()
            data.forEachIndexed { i, s ->
                val x = i * step
                val y = h - ((s.batteryTemp - minTemp) / range * h).coerceIn(0f, h)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.lineTo((data.size - 1) * step, h); path.lineTo(0f, h); path.close()
            drawPath(path, Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.01f))))
            // Línea principal
            val linePath = Path()
            data.forEachIndexed { i, s ->
                val x = i * step
                val y = h - ((s.batteryTemp - minTemp) / range * h).coerceIn(0f, h)
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            drawPath(linePath, accent, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Punto actual con halo
            val lx = (data.size - 1) * step
            val ly = h - ((data.last().batteryTemp - minTemp) / range * h).coerceIn(0f, h)
            drawCircle(accent.copy(alpha = 0.25f), 12f, Offset(lx, ly))
            drawCircle(accent, 5f, Offset(lx, ly))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${minTemp.toInt()}°", fontSize = 9.sp, color = TG.textDim)
            Text("43° zona riesgo", fontSize = 9.sp, color = TG.red.copy(alpha = 0.6f))
            Text("${maxTemp.toInt()}°", fontSize = 9.sp, color = TG.textDim)
        }
    }
}

// ── Ranking de apps ────────────────────────────────────────────────────────
@Composable
fun AppRankingCard(ranking: List<Pair<String, Float>>) {
    if (ranking.isEmpty()) return
    val maxScore = ranking.first().second.coerceAtLeast(35f)
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
            Text("Apps que más calientan", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = TG.textPri)
        }
        ranking.take(5).forEachIndexed { idx, (app, avgTemp) ->
            val barColor = when {
                avgTemp >= 44f -> TG.red
                avgTemp >= 40f -> TG.amber
                else           -> TG.teal
            }
            val pct = ((avgTemp - 30f) / (maxScore - 30f)).coerceIn(0f, 1f)
            val isTop = idx == 0
            Row(
                modifier = Modifier.fillMaxWidth()
                    .let { m -> if (isTop) m.clip(RoundedCornerShape(10.dp))
                        .background(barColor.copy(alpha = 0.07f))
                        .border(1.dp, barColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                    else m.padding(vertical = 2.dp) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (isTop) "🔥" else "${idx + 1}",
                    fontSize = if (isTop) 16.sp else 11.sp, color = TG.textDim,
                    modifier = Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(app, fontSize = 12.sp, fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
                        color = TG.textPri)
                    Box(modifier = Modifier.fillMaxWidth().height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.07f))) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct)
                            .clip(RoundedCornerShape(2.dp)).background(barColor))
                    }
                }
                Text("${avgTemp.toInt()}°", fontSize = 12.sp,
                    color = barColor, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

// ── Perfil horario ──────────────────────────────────────────────────────────
@Composable
fun HourlyProfileCard(hourlyProfile: List<HourlyDataPoint>, accent: Color) {
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
            // Barra de calor por hora
            Row(modifier = Modifier.fillMaxWidth().height(65.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                validHours.forEach { h ->
                    val pct = if (maxT > minT) (h.avgTemp - minT) / (maxT - minT) else 0.5f
                    val c = when {
                        h.avgTemp >= 44f -> TG.red
                        h.avgTemp >= 40f -> TG.amber
                        else             -> accent
                    }
                    Column(modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom) {
                        // Tooltip sutil en la barra más alta
                        if (h.avgTemp == maxT)
                            Text("🔥", fontSize = 7.sp, modifier = Modifier.padding(bottom = 2.dp))
                        Box(modifier = Modifier.fillMaxWidth()
                            .height((pct * 52f).coerceAtLeast(4f).dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(Brush.verticalGradient(listOf(c, c.copy(alpha = 0.4f)))))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("00h", fontSize = 8.sp, color = TG.textDim)
                Text("12h", fontSize = 8.sp, color = TG.textDim)
                Text("23h", fontSize = 8.sp, color = TG.textDim)
            }
            // Pico del día
            validHours.maxByOrNull { it.avgTemp }?.let { peak ->
                Row(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TG.red.copy(alpha = 0.06f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = TG.red, modifier = Modifier.size(12.dp))
                    Text("Pico máximo a las ${peak.hour}h  ·  ${peak.avgTemp.toInt()}°C promedio",
                        fontSize = 11.sp, color = TG.textSec)
                }
            }
        }
    }
}

// ── Sesiones de calor ───────────────────────────────────────────────────────
@Composable
fun HeatSessionsCard(profile: LearnedProfile, accent: Color) {
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
            Icon(Icons.Default.LocalFireDepartment, null, tint = TG.red, modifier = Modifier.size(16.dp))
            Text("Sesiones de calor hoy", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = TG.textPri)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("Sesiones", "${profile.heatSessionsToday}", TG.red, Modifier.weight(1f))
            StatChip("Cooldown", "${profile.avgCooldownMinutes.toInt()} min", TG.teal, Modifier.weight(1f))
            StatChip("Máx", "${profile.maxRecordedTemp.toInt()}°C", TG.amber, Modifier.weight(1f))
        }
    }
}

@Composable
fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 9.sp, color = TG.textSec, textAlign = TextAlign.Center)
    }
}

// ── Risk Score card ───────────────────────────────────────────────────────
@Composable
fun RiskScoreCard(profile: LearnedProfile, accent: Color) {
    val score = profile.riskScore.coerceIn(0, 100)
    val scoreColor = when {
        score >= 70 -> TG.red
        score >= 40 -> TG.amber
        else        -> TG.green
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TG.glass)
            .border(1.dp, scoreColor.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gauge circular de riesgo
        Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(Color.White.copy(alpha = 0.07f), -220f, 260f, false,
                    style = Stroke(7f, cap = StrokeCap.Round))
                drawArc(scoreColor, -220f, 260f * score / 100f, false,
                    style = Stroke(7f, cap = StrokeCap.Round))
                // Punto indicador
                drawCircle(scoreColor.copy(alpha = 0.3f), 6f, center)
                drawCircle(scoreColor, 3f, center)
            }
            Text("$score", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = scoreColor)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Risk Score  ·  Motor v5", fontSize = 10.sp, color = TG.textSec, letterSpacing = 0.4.sp)
            Text(when {
                score >= 70 -> "Nivel alto — tomar acción"
                score >= 40 -> "Moderado — monitorear"
                else        -> "Normal — todo bien"
            }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
            Spacer(Modifier.height(4.dp))
            // Mini barra
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.07f))) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(score / 100f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(TG.green, TG.amber, TG.red))))
            }
            Spacer(Modifier.height(2.dp))
            Text("${profile.samplesCollected} lecturas  ·  umbral ${profile.dynamicThreshold.toInt()}°C",
                fontSize = 10.sp, color = TG.textDim)
        }
    }
}

// ── Battery health card ────────────────────────────────────────────────────
@Composable
fun BatteryHealthCard(health: BatteryHealthScore) {
    val c = when {
        health.score < 40 -> TG.red
        health.score < 70 -> TG.amber
        else              -> TG.green
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TG.glass)
            .border(1.dp, c.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.BatteryFull, null, tint = c, modifier = Modifier.size(16.dp))
            Text("Salud de la batería", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = TG.textPri)
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .background(c.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Text("${health.score}/100", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(6.dp)
            .clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.07f))) {
            Box(modifier = Modifier.fillMaxHeight()
                .fillMaxWidth((health.score / 100f).coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp)).background(c))
        }
        health.factors.take(2).forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("•", fontSize = 11.sp, color = c)
                Text(tip, fontSize = 11.sp, color = TG.textSec, lineHeight = 15.sp)
            }
        }
    }
}
