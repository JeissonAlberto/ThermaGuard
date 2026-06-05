package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.ThermalUiState

@Composable
fun StatsScreen(
    uiState: ThermalUiState,
    onResetLearning: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF1a1a2e), Color(0xFF0F2027))))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.BarChart, null, tint = Color(0xFF80CBC4), modifier = Modifier.size(24.dp))
                Column {
                    Text("Estadisticas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Analisis profundo de tu dispositivo", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }

            // Prediccion
            uiState.prediction?.let { pred ->
                PredictionCard(prediction = pred)
            }

            // Salud de bateria
            uiState.batteryHealth?.let { health ->
                BatteryHealthCard(health = health)
            }

            // Grafica horaria
            if (uiState.hourlyProfile.size >= 3) {
                HourlyProfileCard(hourly = uiState.hourlyProfile)
            }

            // Grafica de historial completo
            if (uiState.history.size >= 5) {
                FullHistoryChart(history = uiState.history.takeLast(50))
            }

            // Estadisticas del perfil
            uiState.profile?.let { profile ->
                if (profile.samplesCollected >= 5) {
                    ProfileStatsCard(profile = profile)
                }
            }

            // Reset aprendizaje
            var showResetDialog by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF9A9A))
            ) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reiniciar motor de aprendizaje")
            }

            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("Reiniciar aprendizaje") },
                    text = { Text("Esto borrara todos los datos aprendidos (baseline, patrones, correlaciones). La app volvera a aprender desde cero.") },
                    confirmButton = {
                        TextButton(onClick = { onResetLearning(); showResetDialog = false }) {
                            Text("Reiniciar", color = Color(0xFFEF9A9A))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) { Text("Cancelar") }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PredictionCard(prediction: TempPrediction) {
    val confColor = when (prediction.confidence) {
        PredictionConfidence.HIGH   -> Color(0xFF00E676)
        PredictionConfidence.MEDIUM -> Color(0xFFFFD600)
        PredictionConfidence.LOW    -> Color(0xFF90A4AE)
    }
    val tempColor = when {
        prediction.predictedTemp >= 45f -> Color(0xFFFF1744)
        prediction.predictedTemp >= 40f -> Color(0xFFFF6D00)
        prediction.predictedTemp >= 35f -> Color(0xFFFFD600)
        else -> Color(0xFF00E676)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoGraph, null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(18.dp))
                Text("Prediccion (proxima lectura)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("~${prediction.predictedTemp.toInt()}°C", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = tempColor)
                    Text(prediction.trendText, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                }
                Surface(shape = RoundedCornerShape(10.dp), color = confColor.copy(alpha = 0.15f)) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(when (prediction.confidence) {
                            PredictionConfidence.HIGH -> "Alta"
                            PredictionConfidence.MEDIUM -> "Media"
                            PredictionConfidence.LOW -> "Baja"
                        }, fontSize = 12.sp, color = confColor, fontWeight = FontWeight.Bold)
                        Text("Confianza", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
            Text(
                "Prediccion basada en regresion lineal sobre las ultimas lecturas.",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun BatteryHealthCard(health: BatteryHealthScore) {
    val scoreColor = when {
        health.score >= 85 -> Color(0xFF00E676)
        health.score >= 70 -> Color(0xFFFFD600)
        health.score >= 50 -> Color(0xFFFF6D00)
        else -> Color(0xFFFF1744)
    }
    val animScore by animateFloatAsState(targetValue = health.score.toFloat(), animationSpec = tween(1200), label = "score")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Favorite, null, tint = scoreColor, modifier = Modifier.size(18.dp))
                Text("Salud de la bateria", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Arco de score
                Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(90.dp)) {
                        val cx = size.width / 2; val cy = size.height / 2; val r = size.width * 0.4f
                        drawArc(Color.White.copy(alpha = 0.08f), 135f, 270f, false,
                            Offset(cx-r, cy-r), Size(r*2, r*2), style = Stroke(10f, cap = StrokeCap.Round))
                        drawArc(scoreColor, 135f, animScore / 100f * 270f, false,
                            Offset(cx-r, cy-r), Size(r*2, r*2), style = Stroke(10f, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${health.score}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = scoreColor)
                        Text("/100", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(health.level, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scoreColor)
                    health.factors.forEach { f ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
                            Text(f, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HourlyProfileCard(hourly: List<HourlyDataPoint>) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, null, tint = Color(0xFFFFCC80), modifier = Modifier.size(18.dp))
                Text("Patron horario aprendido", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                if (hourly.isEmpty()) return@Canvas
                val minT = hourly.minOf { it.avgTemp }
                val maxT = (hourly.maxOf { it.avgTemp } + 2f)
                val range = (maxT - minT).coerceAtLeast(5f)
                val w = size.width; val h = size.height
                val barW = w / 24f

                for (slot in hourly) {
                    val barH = ((slot.avgTemp - minT) / range * h).coerceAtLeast(4f)
                    val x = slot.hour * barW
                    val barColor = when {
                        slot.avgTemp >= 45f -> Color(0xFFFF1744)
                        slot.avgTemp >= 40f -> Color(0xFFFF6D00)
                        slot.avgTemp >= 35f -> Color(0xFFFFD600)
                        else -> Color(0xFF00E676)
                    }
                    drawRect(barColor.copy(alpha = 0.8f), Offset(x + 1f, h - barH), Size(barW - 2f, barH))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("00h", fontSize = 9.sp, color = Color.White.copy(alpha = 0.3f))
                Text("06h", fontSize = 9.sp, color = Color.White.copy(alpha = 0.3f))
                Text("12h", fontSize = 9.sp, color = Color.White.copy(alpha = 0.3f))
                Text("18h", fontSize = 9.sp, color = Color.White.copy(alpha = 0.3f))
                Text("23h", fontSize = 9.sp, color = Color.White.copy(alpha = 0.3f))
            }
            Text("Altura = temperatura promedio registrada por hora del dia",
                fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
        }
    }
}

@Composable
fun FullHistoryChart(history: List<com.jeissonalberto.thermaguard.data.ThermalSnapshot>) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Timeline, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                Text("Ultimas ${history.size} lecturas", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                if (history.size < 2) return@Canvas
                val temps = history.map { it.batteryTemp }
                val minT  = temps.min()
                val maxT  = (temps.max() + 2f)
                val range = (maxT - minT).coerceAtLeast(5f)
                val w = size.width; val h = size.height
                val step = w / (temps.size - 1)

                val pts = temps.mapIndexed { i, t -> Offset(i * step, h - ((t - minT) / range * h)) }

                // Zona critica
                val critY = h - ((45f - minT) / range * h)
                if (critY in 0f..h) {
                    drawLine(Color(0xFFFF1744).copy(alpha = 0.3f), Offset(0f, critY), Offset(w, critY), strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)))
                }

                // Area
                val path = Path().apply {
                    moveTo(pts.first().x, h)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, h); close()
                }
                drawPath(path, Brush.verticalGradient(listOf(Color(0xFF64B5F6).copy(alpha = 0.3f), Color.Transparent)))

                // Linea
                for (i in 0 until pts.size - 1) {
                    val c = when {
                        temps[i] >= 45f -> Color(0xFFFF1744)
                        temps[i] >= 40f -> Color(0xFFFF6D00)
                        else -> Color(0xFF64B5F6)
                    }
                    drawLine(c, pts[i], pts[i+1], strokeWidth = 2f, cap = StrokeCap.Round)
                }
                pts.forEachIndexed { i, p ->
                    if (temps[i] >= 40f) drawCircle(Color(0xFFFF6D00), 4f, p)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Min: ${history.minOf { it.batteryTemp }.toInt()}C", fontSize = 10.sp, color = Color(0xFF00E676))
                Text("--- 45C critico", fontSize = 10.sp, color = Color(0xFFFF1744).copy(alpha=0.6f))
                Text("Max: ${history.maxOf { it.batteryTemp }.toInt()}C", fontSize = 10.sp, color = Color(0xFFEF9A9A))
            }
        }
    }
}

@Composable
fun ProfileStatsCard(profile: LearnedProfile) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ModelTraining, null, tint = Color(0xFFCE93D8), modifier = Modifier.size(18.dp))
                Text("Perfil del motor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            val rows = listOf(
                Triple("Baseline personal", "${profile.baselineTemp.toInt()}°C", Color(0xFF80CBC4)),
                Triple("Temp promedio", "${profile.averageTemp.toInt()}°C", Color(0xFFFFCC80)),
                Triple("Maximo historico", "${profile.maxRecordedTemp.toInt()}°C", Color(0xFFEF9A9A)),
                Triple("Minimo historico", if (profile.minRecordedTemp > 0f) "${profile.minRecordedTemp.toInt()}°C" else "N/D", Color(0xFF80CBC4)),
                Triple("Umbral dinamico", "${profile.dynamicThreshold.toInt()}°C", Color(0xFFFFD54F)),
                Triple("Muestras", "${profile.samplesCollected}", Color(0xFFCE93D8)),
                Triple("App mas caliente", profile.topHeatApp.ifEmpty { "N/D" }, Color(0xFFFF8A65)),
            )
            rows.forEach { (label, value, color) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    Text(value, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
                }
            }
            // Precision del modelo
            val pct = (profile.samplesCollected / 100f).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Precision del modelo", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    Text("${(pct*100).toInt()}%", fontSize = 12.sp, color = Color(0xFFCE93D8), fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = pct,
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFFCE93D8),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }
    }
}
