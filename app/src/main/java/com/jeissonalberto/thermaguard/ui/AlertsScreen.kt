package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.jeissonalberto.thermaguard.domain.AutoAction
import com.jeissonalberto.thermaguard.domain.ThermalUiState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AlertsScreen(
    uiState: ThermalUiState,
    onThresholdChange: (Float) -> Unit,
    onClearLog: () -> Unit
) {
    val snap  = uiState.latest
    val level = snap.batteryTemp.toThermalLevel()

    val bg = when (level) {
        ThermalLevel.NORMAL    -> Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF1a1a2e)))
        ThermalLevel.WARM      -> Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF0F2027)))
        ThermalLevel.HOT       -> Brush.verticalGradient(listOf(Color(0xFF2D1B00), Color(0xFF0F2027)))
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY -> Brush.verticalGradient(listOf(Color(0xFF3D0000), Color(0xFF1a0000)))
    }

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFFFF8A65), modifier = Modifier.size(24.dp))
                    Column {
                        Text("Alertas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Motor activo — optimiza en segundo plano", fontSize = 11.sp, color = Color(0xFF00C853))
                    }
                }
                // Indicador de motor activo (siempre ON)
                Surface(shape = CircleShape, color = Color(0xFF00C853).copy(alpha = 0.15f)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF00C853)))
                        Text("AUTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00C853))
                    }
                }
            }

            // Umbral dinámico aprendido
            ThresholdCard(
                threshold = uiState.alertThreshold,
                onThresholdChange = onThresholdChange
            )

            Spacer(Modifier.height(14.dp))

            // Log de acciones automáticas ejecutadas
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Acciones del motor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f))
                if (uiState.autoActionsLog.isNotEmpty()) {
                    TextButton(onClick = onClearLog) {
                        Text("Limpiar", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }

            if (uiState.autoActionsLog.isEmpty()) {
                EmptyLogCard()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.autoActionsLog) { action ->
                        AutoActionCard(action = action)
                    }
                }
            }
        }
    }
}

@Composable
fun ThresholdCard(threshold: Float, onThresholdChange: (Float) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFCE93D8), modifier = Modifier.size(16.dp))
                Text("Umbral de acción automática", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFCE93D8).copy(alpha = 0.15f)) {
                    Text("${threshold.toInt()}°C",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCE93D8))
                }
            }
            Text("El motor actúa automáticamente cuando la temperatura supera este valor. " +
                "Se ajusta solo con el tiempo según tu uso.", fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f), lineHeight = 16.sp)

            Slider(
                value     = threshold,
                onValueChange = onThresholdChange,
                valueRange = 38f..50f,
                steps     = 11,
                colors    = SliderDefaults.colors(thumbColor = Color(0xFFCE93D8), activeTrackColor = Color(0xFFCE93D8))
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("38°C", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
                Text("50°C", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
fun AutoActionCard(action: AutoAction) {
    val sdf = remember { SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()) }
    val isWarning = action.description.startsWith("⚠️")
    val color = if (isWarning) Color(0xFFFFB300) else Color(0xFF00C853)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isWarning) Icons.Default.Warning else Icons.Default.AutoFixHigh,
                    null, tint = color, modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(action.description, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White, lineHeight = 17.sp)
                Text("Activado por: ${action.trigger}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f))
            }
            Text(sdf.format(Date(action.timestamp)), fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
        }
    }
}

@Composable
fun EmptyLogCard() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.04f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00C853), modifier = Modifier.size(32.dp))
            Text("Todo en orden", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Text("El motor no ha necesitado actuar todavía.\nCuando detecte calor anormal, actuará automáticamente.",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f),
                lineHeight = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
