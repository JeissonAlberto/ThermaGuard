package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.ThermalUiState

@Composable
fun AlertsScreen(
    uiState: ThermalUiState,
    onThresholdChange: (Float) -> Unit,
    onToggleAutoMode: () -> Unit
) {
    val snap = uiState.latest
    val level = snap.batteryTemp.toThermalLevel()
    val scrollState = rememberScrollState()

    val bgColor = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF0F2027)
        ThermalLevel.WARM      -> Color(0xFF1A1A2E)
        ThermalLevel.HOT       -> Color(0xFF2D1B00)
        ThermalLevel.CRITICAL  -> Color(0xFF3D0000)
        ThermalLevel.EMERGENCY -> Color(0xFF4A0000)
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFFFF8A65), modifier = Modifier.size(24.dp))
                Column {
                    Text("Centro de Alertas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Inteligencia adaptativa activa", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }

            // Estado actual - card grande
            CurrentStateCard(snap = snap, level = level, profile = uiState.profile)

            // Smart tips del motor de aprendizaje
            if (uiState.smartTips.isNotEmpty()) {
                SmartTipsSection(tips = uiState.smartTips)
            }

            // Configuracion de umbral
            ThresholdConfig(
                threshold = uiState.alertThreshold,
                autoMode = uiState.autoMode,
                onThresholdChange = onThresholdChange,
                onToggleAutoMode = onToggleAutoMode
            )

            // Plan de accion
            if (uiState.optimizationPlan.isNotEmpty()) {
                ActionPlanSection(plan = uiState.optimizationPlan)
            }

            // Estadisticas de aprendizaje
            uiState.profile?.let { profile ->
                if (profile.samplesCollected >= 3) {
                    LearningStatsSection(profile = profile)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun CurrentStateCard(
    snap: com.jeissonalberto.thermaguard.data.ThermalSnapshot,
    level: ThermalLevel,
    profile: LearnedProfile?
) {
    val borderColor = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF00E676)
        ThermalLevel.WARM      -> Color(0xFFFFD600)
        ThermalLevel.HOT       -> Color(0xFFFF6D00)
        ThermalLevel.CRITICAL  -> Color(0xFFFF1744)
        ThermalLevel.EMERGENCY -> Color(0xFFD50000)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth().border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Estado actual", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(level.emoji, fontSize = 28.sp)
                        Text("${snap.batteryTemp}°C", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = borderColor)
                    }
                    Text(level.label.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = borderColor.copy(alpha = 0.8f), letterSpacing = 1.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusPill(label = if (snap.isCharging) "Cargando" else "Descargando",
                        color = if (snap.isCharging) Color(0xFF69F0AE) else Color(0xFFFFCC80))
                    Spacer(Modifier.height(4.dp))
                    StatusPill(label = "CPU ${snap.cpuUsage.toInt()}%",
                        color = if (snap.cpuUsage > 70) Color(0xFFEF9A9A) else Color(0xFF80CBC4))
                    if (snap.topApp.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        StatusPill(label = snap.topApp, color = Color(0xFFCE93D8))
                    }
                }
            }

            // Barra de temperatura
            TempProgressBar(temp = snap.batteryTemp)

            // Anomalia badge
            profile?.let { p ->
                if (p.isAnomaly) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFF1744).copy(alpha = 0.15f)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF8A65), modifier = Modifier.size(16.dp))
                            Text("Anomalia detectada: +${p.tempAnomaly.toInt()}C sobre tu baseline (${p.baselineTemp.toInt()}C)",
                                fontSize = 12.sp, color = Color(0xFFFF8A65))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TempProgressBar(temp: Float) {
    val progress = (temp / 60f).coerceIn(0f, 1f)
    val animProg by animateFloatAsState(targetValue = progress, animationSpec = tween(600), label = "prog")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0°C", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
            Text("30°C", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
            Text("45°C", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
            Text("60°C", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
        }
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.1f))) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(animProg)
                .background(Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFFFFD600), Color(0xFFFF6D00), Color(0xFFFF1744))))
                .clip(RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun StatusPill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SmartTipsSection(tips: List<SmartTip>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
            Text("Consejos inteligentes", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        tips.forEach { tip ->
            SmartTipCard(tip = tip)
        }
    }
}

@Composable
fun SmartTipCard(tip: SmartTip) {
    var expanded by remember { mutableStateOf(tip.priority >= 4) }

    val borderColor = when (tip.priority) {
        5    -> Color(0xFFFF1744)
        4    -> Color(0xFFFF6D00)
        3    -> Color(0xFFFFD600)
        else -> Color(0xFF00E676)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth().border(0.5.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)) {
                    // Priority indicator
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(borderColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Text(tip.icon, fontSize = 18.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tip.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        if (tip.priority >= 4) {
                            Text(
                                when (tip.priority) { 5 -> "Critico" 4 -> "Importante" else -> "" },
                                fontSize = 10.sp, color = borderColor, letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(Modifier.height(10.dp))
                    Text(tip.detail, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
fun ThresholdConfig(
    threshold: Float,
    autoMode: Boolean,
    onThresholdChange: (Float) -> Unit,
    onToggleAutoMode: () -> Unit
) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Tune, null, tint = Color(0xFF80CBC4), modifier = Modifier.size(18.dp))
                Text("Configuracion de alertas", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Umbral de alerta", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    Text("${threshold.toInt()}°C", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFCC80))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { if (threshold > 35f) onThresholdChange(threshold - 1f) },
                        modifier = Modifier.size(36.dp).background(Color.White.copy(alpha=0.1f), CircleShape)) {
                        Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { if (threshold < 55f) onThresholdChange(threshold + 1f) },
                        modifier = Modifier.size(36.dp).background(Color.White.copy(alpha=0.1f), CircleShape)) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Slider(
                value = threshold, onValueChange = onThresholdChange,
                valueRange = 35f..55f, steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFCC80),
                    activeTrackColor = Color(0xFFFF6D00),
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("35°C (conservador)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
                Text("55°C (permisivo)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
            }

            Divider(color = Color.White.copy(alpha = 0.08f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Modo automatico", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    Text("Optimiza sin intervenci\u00f3n manual", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                }
                Switch(
                    checked = autoMode, onCheckedChange = { onToggleAutoMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00C853)
                    )
                )
            }
        }
    }
}

@Composable
fun ActionPlanSection(plan: List<com.jeissonalberto.thermaguard.data.OptimizationAction>) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PlaylistAddCheck, null, tint = Color(0xFFCE93D8), modifier = Modifier.size(18.dp))
                Text("Plan de acci\u00f3n recomendado", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            plan.forEach { action ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CheckCircleOutline, null, tint = Color(0xFFCE93D8), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Column {
                        Text(action.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        Text(action.description, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
fun LearningStatsSection(profile: LearnedProfile) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ModelTraining, null, tint = Color(0xFF80CBC4), modifier = Modifier.size(18.dp))
                Text("Motor de aprendizaje", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            // Progress del aprendizaje
            val learnPct = (profile.samplesCollected / 100f).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Precision del modelo", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    Text("${(learnPct * 100).toInt()}%", fontSize = 12.sp, color = Color(0xFF80CBC4), fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = learnPct,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF80CBC4),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                Text("${profile.samplesCollected}/100 muestras para precision maxima",
                    fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
            }

            // Patrones detectados
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PatternStat("Carga", "${profile.chargingHeatPct.toInt()}%", Color(0xFFFFD54F))
                PatternStat("CPU alta", "${profile.highCpuHeatPct.toInt()}%", Color(0xFFEF9A9A))
                PatternStat("Muestras", "${profile.samplesCollected}", Color(0xFF80CBC4))
            }

            // Causa mas probable
            val causeText = when (profile.likelyCause) {
                LearnedCause.CHARGING_HABIT -> "⚡ Patron: calentamiento al cargar"
                LearnedCause.HIGH_CPU_APPS -> "📱 Patron: apps de alto consumo"
                LearnedCause.BACKGROUND_DRAIN -> "👻 Patron: drenaje en background"
                LearnedCause.UNKNOWN -> "🔍 Aprendiendo patrones..."
            }
            Text(causeText, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun PatternStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
    }
}
