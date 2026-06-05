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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.ThermalUiState

@Composable
fun OptimizeScreen(
    uiState: ThermalUiState,
    onCoolingMode: () -> Unit,
    onKillApps: () -> Unit,
    onFreeRam: () -> Unit
) {
    val context = LocalContext.current
    val snap    = uiState.latest
    val level   = snap.batteryTemp.toThermalLevel()
    val scroll  = rememberScrollState()

    val bgColor = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF0F2027)
        ThermalLevel.WARM      -> Color(0xFF1A1A2E)
        ThermalLevel.HOT       -> Color(0xFF2D1B00)
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY -> Color(0xFF3D0000)
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bgColor, Color(0xFF0F2027))))) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Tune, null, tint = Color(0xFF00C853), modifier = Modifier.size(24.dp))
                Column {
                    Text("Optimizar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Acciones directas para enfriar tu dispositivo", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }

            // Estado actual resumido
            CurrentTempBanner(snap = snap, level = level)

            // BOTON MODO COOLING — accion principal
            CoolingModeButton(
                temp  = snap.batteryTemp,
                level = level,
                coolingResult = uiState.coolingResult,
                onPress = onCoolingMode
            )

            // Acciones rapidas ejecutables directamente
            Text("Acciones inmediatas", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Close,
                    title = "Cerrar apps",
                    subtitle = "Mata procesos background",
                    color = Color(0xFFFF6D00),
                    onClick = onKillApps
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Memory,
                    title = "Liberar RAM",
                    subtitle = "Limpia cache del sistema",
                    color = Color(0xFF64B5F6),
                    onClick = onFreeRam
                )
            }

            // Acciones que abren Ajustes directamente
            Text("Ajustes del sistema", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f))

            val settingsActions = listOf(
                SettingsAction(
                    icon = Icons.Default.BatterySaver,
                    title = "Activar ahorro de bateria",
                    description = "Limita rendimiento y reduce calor inmediatamente",
                    color = Color(0xFF69F0AE),
                    intent = android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS),
                    urgency = if (snap.batteryTemp >= 42f) ActionUrgency.HIGH else ActionUrgency.NORMAL
                ),
                SettingsAction(
                    icon = Icons.Default.Brightness4,
                    title = "Reducir brillo",
                    description = "Pantalla al maximo genera calor notable en AMOLED",
                    color = Color(0xFFFFD54F),
                    intent = android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS),
                    urgency = if (snap.brightnessLevel > 200) ActionUrgency.HIGH else ActionUrgency.NORMAL
                ),
                SettingsAction(
                    icon = Icons.Default.AirplanemodeActive,
                    title = "Modo avion (5 min)",
                    description = "Apaga WiFi, datos y BT. Enfria el modem rapidamente",
                    color = Color(0xFF80CBC4),
                    intent = android.content.Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS),
                    urgency = if (snap.modemTemp > 44f) ActionUrgency.HIGH else ActionUrgency.NORMAL
                ),
                SettingsAction(
                    icon = Icons.Default.Bluetooth,
                    title = "Desactivar Bluetooth",
                    description = "BT activo sin uso consume energia innecesaria",
                    color = Color(0xFF7986CB),
                    intent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
                    urgency = if (snap.bluetoothActive) ActionUrgency.SUGGESTED else ActionUrgency.NORMAL
                ),
                SettingsAction(
                    icon = Icons.Default.BatteryFull,
                    title = "Ver uso de bateria",
                    description = "Identifica que app esta consumiendo mas energia",
                    color = Color(0xFFFFCC80),
                    intent = try { android.content.Intent(android.content.Intent.ACTION_POWER_USAGE_SUMMARY) }
                             catch (e: Exception) { android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS) },
                    urgency = ActionUrgency.NORMAL
                ),
                SettingsAction(
                    icon = Icons.Default.Sync,
                    title = "Desactivar sincronizacion",
                    description = "La sincronizacion en background usa CPU y datos",
                    color = Color(0xFFCE93D8),
                    intent = android.content.Intent(android.provider.Settings.ACTION_SYNC_SETTINGS),
                    urgency = ActionUrgency.NORMAL
                ),
            )

            // Mostrar primero las urgentes
            val sorted = settingsActions.sortedByDescending { it.urgency.ordinal }
            sorted.forEach { action ->
                SettingsActionCard(action = action, context = context)
            }

            // Si hay app caliente, mostrar opcion de forzar cierre
            if (snap.topApp.isNotEmpty() && snap.cpuUsage > 50f) {
                HotAppCard(appName = snap.topApp, cpuPct = snap.cpuUsage, context = context)
            }

            // Plan recomendado del motor
            if (uiState.optimizationPlan.isNotEmpty()) {
                OptimizationPlanCard(plan = uiState.optimizationPlan)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun CurrentTempBanner(snap: ThermalSnapshot, level: ThermalLevel) {
    val color = when (level) {
        ThermalLevel.NORMAL    -> Color(0xFF00E676)
        ThermalLevel.WARM      -> Color(0xFFFFD600)
        ThermalLevel.HOT       -> Color(0xFFFF6D00)
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY -> Color(0xFFFF1744)
    }
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(level.emoji, fontSize = 28.sp)
                Column {
                    Text("${snap.batteryTemp}°C — ${level.label}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
                    Text("CPU ${snap.cpuUsage.toInt()}%  |  ${if (snap.isCharging) "⚡ Cargando" else "🔋 ${snap.batteryLevel}%"}",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
            if (snap.topApp.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.06f)) {
                    Text(snap.topApp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun CoolingModeButton(
    temp: Float,
    level: ThermalLevel,
    coolingResult: CoolingResult?,
    onPress: () -> Unit
) {
    val btnColor = when (level) {
        ThermalLevel.NORMAL, ThermalLevel.WARM -> Color(0xFF00C853)
        ThermalLevel.HOT                       -> Color(0xFFFF6D00)
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY                 -> Color(0xFFFF1744)
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(0.7f, 1f,
        infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse), label = "a")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPress,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = btnColor.copy(alpha = if (temp >= 40f) alpha else 1f))
        ) {
            Icon(Icons.Default.AcUnit, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Modo Cooling — Activar ahora", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Cierra apps, libera RAM y guia optimizacion", fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.75f))
            }
        }

        // Resultado del ultimo cooling
        AnimatedVisibility(visible = coolingResult != null) {
            coolingResult?.let { result ->
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF00C853).copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00C853).copy(alpha=0.3f), RoundedCornerShape(12.dp))) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00C853), modifier = Modifier.size(16.dp))
                            Text("Cooling aplicado", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00C853))
                        }
                        result.actionsApplied.forEach { action ->
                            Text("• $action", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                        Divider(color = Color.White.copy(alpha = 0.08f))
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.ArrowForward, null, tint = Color(0xFFFFCC80), modifier = Modifier.size(14.dp).padding(top=1.dp))
                            Text(result.nextStep, fontSize = 12.sp, color = Color(0xFFFFCC80), lineHeight = 17.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.1f),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha=0.15f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

enum class ActionUrgency { NORMAL, SUGGESTED, HIGH }

data class SettingsAction(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color,
    val intent: android.content.Intent,
    val urgency: ActionUrgency
)

@Composable
fun SettingsActionCard(action: SettingsAction, context: android.content.Context) {
    val borderColor = when (action.urgency) {
        ActionUrgency.HIGH      -> action.color.copy(alpha = 0.6f)
        ActionUrgency.SUGGESTED -> action.color.copy(alpha = 0.3f)
        ActionUrgency.NORMAL    -> Color.White.copy(alpha = 0.08f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.04f),
        modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(14.dp)),
        onClick = {
            try { context.startActivity(action.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (e: Exception) { }
        }
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(action.color.copy(alpha=0.12f)),
                contentAlignment = Alignment.Center) {
                Icon(action.icon, null, tint = action.color, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(action.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    if (action.urgency == ActionUrgency.HIGH) {
                        Surface(shape = RoundedCornerShape(4.dp), color = action.color.copy(alpha=0.2f)) {
                            Text("Recomendado", modifier = Modifier.padding(horizontal=5.dp, vertical=1.dp),
                                fontSize = 9.sp, color = action.color)
                        }
                    }
                }
                Text(action.description, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), lineHeight = 16.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun HotAppCard(appName: String, cpuPct: Float, context: android.content.Context) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFF6D00).copy(alpha=0.08f),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFF6D00).copy(alpha=0.4f), RoundedCornerShape(14.dp)),
        onClick = {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$appName")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) { }
        }) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFF6D00).copy(alpha=0.15f)),
                contentAlignment = Alignment.Center) {
                Text("🔥", fontSize = 20.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Forzar cierre de '$appName'", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text("CPU al ${cpuPct.toInt()}% — toca para ir a Ajustes > Apps", fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f))
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFFF6D00).copy(alpha=0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun OptimizationPlanCard(plan: List<OptimizationAction>) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.05f)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFCE93D8), modifier = Modifier.size(16.dp))
                Text("Plan recomendado por el motor", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            plan.forEach { action ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.RadioButtonUnchecked, null, tint = Color(0xFFCE93D8),
                        modifier = Modifier.size(14.dp).padding(top=2.dp))
                    Column {
                        Text(action.title, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        Text(action.description, fontSize = 11.sp, color = Color.White.copy(alpha=0.5f))
                    }
                }
            }
        }
    }
}
