package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
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

@Composable
fun DashboardScreen(
    uiState: ThermalUiState,
    onToggleMonitor: () -> Unit,
    onToggleAutoMode: () -> Unit
) {
    val scrollState = rememberScrollState()
    val snap = uiState.latest
    val level = snap.batteryTemp.toThermalLevel()

    val bgGradient = when (level) {
        ThermalLevel.NORMAL   -> listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
        ThermalLevel.WARM     -> listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
        ThermalLevel.HOT      -> listOf(Color(0xFF2D1B00), Color(0xFF4A2800), Color(0xFF6B3500))
        ThermalLevel.CRITICAL -> listOf(Color(0xFF3D0000), Color(0xFF5C0000), Color(0xFF7A0000))
        ThermalLevel.EMERGENCY-> listOf(Color(0xFF4A0000), Color(0xFF6B0000), Color(0xFF8B0000))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgGradient))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ThermaGuard", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (uiState.profile?.samplesCollected ?: 0 > 0)
                            "${uiState.profile!!.samplesCollected} muestras aprendidas"
                        else "Aprendiendo tu dispositivo...",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Auto mode pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (uiState.autoMode) Color(0xFF00C853).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = if (uiState.autoMode) Color(0xFF00C853) else Color.White.copy(alpha=0.5f), modifier = Modifier.size(14.dp))
                            Text("Auto", fontSize = 12.sp, color = if (uiState.autoMode) Color(0xFF00C853) else Color.White.copy(alpha=0.5f))
                        }
                    }
                }
            }

            // GAUGE principal
            ThermalGauge(temp = snap.batteryTemp, level = level)

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(modifier = Modifier.weight(1f), label = "CPU", value = "${snap.cpuUsage.toInt()}%",
                    icon = Icons.Default.Memory, color = Color(0xFF64B5F6))
                StatCard(modifier = Modifier.weight(1f), label = "Bateria", value = "${snap.batteryLevel}%",
                    icon = if (snap.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.Battery5Bar,
                    color = if (snap.isCharging) Color(0xFF69F0AE) else Color(0xFFFFD54F))
                StatCard(modifier = Modifier.weight(1f), label = "CPU Zona", value = "${snap.cpuTemp.toInt()}C",
                    icon = Icons.Default.Thermostat, color = Color(0xFFFF8A65))
            }

            // Mini grafica de historial
            if (uiState.history.size >= 3) {
                TempMiniChart(history = uiState.history.takeLast(20))
            }

            // Perfil aprendido
            uiState.profile?.let { profile ->
                if (profile.samplesCollected >= 5) {
                    LearnedProfileCard(profile = profile)
                }
            }

            // Causas detectadas
            if (uiState.causes.isNotEmpty()) {
                CausesCard(causes = uiState.causes)
            }

            // Botones de control
            ControlButtons(
                isMonitoring = uiState.isMonitoring,
                autoMode = uiState.autoMode,
                onToggleMonitor = onToggleMonitor,
                onToggleAutoMode = onToggleAutoMode
            )

            // Info extra
            if (snap.topApp.isNotEmpty()) {
                InfoRow("App activa", snap.topApp)
            }
            InfoRow("Superfice", if (snap.skinTemp > 0f) "${snap.skinTemp.toInt()}C" else "N/D")
            InfoRow("Brillo", "${(snap.brightnessLevel * 100 / 255)}%")

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ThermalGauge(temp: Float, level: ThermalLevel) {
    val animatedTemp by animateFloatAsState(targetValue = temp, animationSpec = tween(800), label = "temp")

    val gaugeColor = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF00E676)
        ThermalLevel.WARM      -> Color(0xFFFFD600)
        ThermalLevel.HOT       -> Color(0xFFFF6D00)
        ThermalLevel.CRITICAL  -> Color(0xFFFF1744)
        ThermalLevel.EMERGENCY -> Color(0xFFD50000)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val radius = size.width * 0.42f
            val strokeW = 18f

            // Fondo del arco
            drawArc(color = Color.White.copy(alpha = 0.08f),
                startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Round))

            // Arco de temperatura
            val sweep = (animatedTemp / 60f).coerceIn(0f, 1f) * 270f
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFF00E676), Color(0xFFFFD600), Color(0xFFFF6D00), Color(0xFFFF1744))),
                startAngle = 135f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Round))

            // Marcas de temperatura
            for (i in 0..6) {
                val angle = 135f + i * 45f
                val rad = Math.toRadians(angle.toDouble())
                val inner = radius - strokeW - 8
                val outer = radius - strokeW - 18
                drawLine(color = Color.White.copy(alpha = 0.3f),
                    start = Offset((cx + cos(rad) * inner).toFloat(), (cy + sin(rad) * inner).toFloat()),
                    end = Offset((cx + cos(rad) * outer).toFloat(), (cy + sin(rad) * outer).toFloat()),
                    strokeWidth = 2f)
            }

            // Punto indicador
            if (sweep > 0f) {
                val endAngle = 135f + sweep
                val rad = Math.toRadians(endAngle.toDouble())
                drawCircle(color = gaugeColor,
                    radius = 10f * pulse,
                    center = Offset((cx + cos(rad) * radius).toFloat(), (cy + sin(rad) * radius).toFloat()))
                drawCircle(color = gaugeColor.copy(alpha = 0.3f),
                    radius = 18f * pulse,
                    center = Offset((cx + cos(rad) * radius).toFloat(), (cy + sin(rad) * radius).toFloat()))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(level.emoji, fontSize = 32.sp)
            Text(
                text = "${animatedTemp.toInt()}°C",
                fontSize = 48.sp, fontWeight = FontWeight.Bold,
                color = gaugeColor
            )
            Text(level.label, fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun StatCard(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun TempMiniChart(history: List<com.jeissonalberto.thermaguard.data.ThermalSnapshot>) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.08f)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Historial reciente", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(8.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(70.dp)) {
                if (history.size < 2) return@Canvas
                val temps = history.map { it.batteryTemp }
                val minT = temps.min()
                val maxT = (temps.max() + 2f)
                val range = (maxT - minT).coerceAtLeast(5f)
                val w = size.width
                val h = size.height
                val step = w / (temps.size - 1)

                val points = temps.mapIndexed { i, t ->
                    Offset(i * step, h - ((t - minT) / range * h))
                }

                // Area rellena
                val path = Path().apply {
                    moveTo(points.first().x, h)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, h)
                    close()
                }
                drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFF6D00).copy(alpha = 0.4f), Color.Transparent)))

                // Linea
                for (i in 0 until points.size - 1) {
                    drawLine(Color(0xFFFF6D00), points[i], points[i + 1], strokeWidth = 2.5f, cap = StrokeCap.Round)
                }

                // Puntos
                points.forEach { drawCircle(Color(0xFFFF8A65), 3f, it) }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${history.size} lecturas", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
                Text("Min: ${history.minOf { it.batteryTemp }.toInt()}C  Max: ${history.maxOf { it.batteryTemp }.toInt()}C",
                    fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun LearnedProfileCard(profile: com.jeissonalberto.thermaguard.data.LearnedProfile) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.08f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Psychology, null, tint = Color(0xFFCE93D8), modifier = Modifier.size(18.dp))
                Text("Perfil aprendido", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProfileStat("Baseline", "${profile.baselineTemp.toInt()}C", Color(0xFF80CBC4))
                ProfileStat("Promedio", "${profile.averageTemp.toInt()}C", Color(0xFFFFCC80))
                ProfileStat("Max record", "${profile.maxRecordedTemp.toInt()}C", Color(0xFFEF9A9A))
            }
            // Tendencia
            val trendText = when (profile.trend) {
                TempTrend.STABLE -> "Estable ✅"
                TempTrend.RISING -> "Subiendo 📈"
                TempTrend.RISING_FAST -> "Ascenso rapido ⚠️"
            }
            val riskText = when (profile.personalRisk) {
                RiskLevel.NORMAL -> "Sin riesgo"
                RiskLevel.LOW -> "Riesgo bajo"
                RiskLevel.MEDIUM -> "Riesgo medio"
                RiskLevel.HIGH -> "Riesgo alto"
                RiskLevel.CRITICAL -> "RIESGO CRITICO"
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tendencia: $trendText", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                Text(riskText, fontSize = 11.sp, color = when (profile.personalRisk) {
                    RiskLevel.NORMAL, RiskLevel.LOW -> Color(0xFF80CBC4)
                    RiskLevel.MEDIUM -> Color(0xFFFFCC80)
                    RiskLevel.HIGH, RiskLevel.CRITICAL -> Color(0xFFEF9A9A)
                })
            }
        }
    }
}

@Composable
fun ProfileStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
fun CausesCard(causes: List<com.jeissonalberto.thermaguard.data.HeatCause>) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.08f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.BugReport, null, tint = Color(0xFFFF8A65), modifier = Modifier.size(18.dp))
                Text("Causas detectadas", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
            }
            causes.take(3).forEach { cause ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sevColor = when {
                        cause.severity >= 4 -> Color(0xFFEF9A9A)
                        cause.severity >= 3 -> Color(0xFFFFCC80)
                        else -> Color(0xFF80CBC4)
                    }
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(sevColor).padding(top = 6.dp))
                    Column {
                        Text(cause.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        Text(cause.description, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
fun ControlButtons(isMonitoring: Boolean, autoMode: Boolean, onToggleMonitor: () -> Unit, onToggleAutoMode: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onToggleMonitor,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMonitoring) Color(0xFFEF5350) else Color(0xFF00C853)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (isMonitoring) "Detener" else "Monitorear")
        }
        OutlinedButton(
            onClick = onToggleAutoMode,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (autoMode) Color(0xFF00C853) else Color.White)
        ) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (autoMode) "Auto ON" else "Auto OFF")
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        Text(value, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
    }
}
