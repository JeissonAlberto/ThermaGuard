package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.ThermalLevel
import com.jeissonalberto.thermaguard.data.ThermalSnapshot
import com.jeissonalberto.thermaguard.data.toThermalLevel
import com.jeissonalberto.thermaguard.domain.ThermalUiState

@Composable
fun DashboardScreen(
    uiState: ThermalUiState,
    onToggleMonitor: () -> Unit,
    onToggleAutoMode: () -> Unit
) {
    val scrollState = rememberScrollState()

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
            Text(
                text = "🌡️ ThermaGuard",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = uiState.isMonitoring,
                onCheckedChange = { onToggleMonitor() }
            )
        }

        // Temperatura principal
        TempGauge(snapshot = uiState.latest)

        // Métricas secundarias
        MetricsRow(snapshot = uiState.latest)

        // Causas de calentamiento
        if (uiState.causes.isNotEmpty()) {
            CausesCard(causes = uiState.causes)
        }

        // Plan de optimización
        if (uiState.optimizationPlan.isNotEmpty()) {
            OptimizationCard(
                plan = uiState.optimizationPlan,
                autoMode = uiState.autoMode,
                onToggleAuto = onToggleAutoMode
            )
        }

        // Estado del sistema
        SystemStatusCard(snapshot = uiState.latest)

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun TempGauge(snapshot: ThermalSnapshot) {
    val level = snapshot.batteryTemp.toThermalLevel()
    val color = when (level) {
        ThermalLevel.NORMAL -> Color(0xFF4CAF50)
        ThermalLevel.WARM -> Color(0xFFFFEB3B)
        ThermalLevel.HOT -> Color(0xFFFF9800)
        ThermalLevel.CRITICAL -> Color(0xFFF44336)
        ThermalLevel.EMERGENCY -> Color(0xFF9C27B0)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = if (level == ThermalLevel.CRITICAL || level == ThermalLevel.EMERGENCY) 0.4f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${snapshot.batteryTemp.toInt()}°C",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Text(
                text = "${level.emoji} ${level.label}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Batería: ${snapshot.batteryLevel}% · ${if (snapshot.isCharging) "⚡ Cargando" else "🔋 Descargando"}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricsRow(snapshot: ThermalSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard(
            modifier = Modifier.weight(1f),
            icon = "🖥️",
            label = "CPU",
            value = "${snapshot.cpuTemp.toInt()}°C",
            sub = "${snapshot.cpuUsage.toInt()}% uso"
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            icon = "🎮",
            label = "GPU",
            value = if (snapshot.gpuTemp > 0) "${snapshot.gpuTemp.toInt()}°C" else "N/D",
            sub = "Temperatura GPU"
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            icon = "📱",
            label = "Skin",
            value = if (snapshot.skinTemp > 0) "${snapshot.skinTemp.toInt()}°C" else "N/D",
            sub = "Superficie"
        )
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    icon: String,
    label: String,
    value: String,
    sub: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = icon, fontSize = 20.sp)
            Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text = sub, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CausesCard(causes: List<com.jeissonalberto.thermaguard.data.HeatCause>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "⚠️ Causas detectadas",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            causes.forEach { cause ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    val sev = "🔥".repeat(minOf(cause.severity, 3))
                    Text(text = sev, fontSize = 14.sp)
                    Column {
                        Text(text = cause.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = cause.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OptimizationCard(
    plan: List<com.jeissonalberto.thermaguard.data.OptimizationAction>,
    autoMode: Boolean,
    onToggleAuto: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "⚡ Plan de optimización", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Auto", fontSize = 12.sp)
                    Switch(checked = autoMode, onCheckedChange = { onToggleAuto() })
                }
            }
            plan.forEach { action ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(text = action.title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(text = action.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SystemStatusCard(snapshot: ThermalSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "📊 Estado del sistema", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            StatusRow("App activa", snapshot.topApp.ifEmpty { "Desconocida" })
            StatusRow("WiFi", if (snapshot.wifiActive) "🟢 Activo" else "⚫ Inactivo")
            StatusRow("Bluetooth", if (snapshot.bluetoothActive) "🟢 Activo" else "⚫ Inactivo")
            StatusRow("Brillo", "${(snapshot.brightnessLevel / 255f * 100).toInt()}%")
            StatusRow("Estado térmico sistema", thermalStatusLabel(snapshot.thermalStatus))
        }
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

fun thermalStatusLabel(status: Int): String = when (status) {
    0 -> "🟢 Normal"
    1 -> "🟡 Leve"
    2 -> "🟠 Moderado"
    3 -> "🔴 Severo"
    4 -> "🚨 Crítico"
    5 -> "💥 Emergencia"
    else -> "Desconocido"
}
