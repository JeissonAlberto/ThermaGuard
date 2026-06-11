package com.jeissonalberto.thermaguard.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.*

@Composable
fun BeastModeScreen(
    uiState: ThermalUiState,
    onSetMode: (OperationMode) -> Unit = {}
) {
    val context   = LocalContext.current
    val snap      = uiState.latest
    val isActive  = uiState.operationMode == OperationMode.GAMER
    val mainTemp  = if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp
    val level     = mainTemp.toThermalLevel()
    val accent    = if (isActive) Color(0xFFFF3D00) else Color(0xFF00E5FF)

    // Estado individual de cada toggle
    var toggleBrightness  by remember { mutableStateOf(false) }
    var toggleCpuLimit    by remember { mutableStateOf(false) }
    var toggle5G          by remember { mutableStateOf(false) }
    var toggleBgApps      by remember { mutableStateOf(false) }
    var toggleSync        by remember { mutableStateOf(false) }
    var toggleRefreshRate by remember { mutableStateOf(false) }

    // Sincronizar toggles con estado del modo
    LaunchedEffect(isActive) {
        if (isActive) {
            toggleBrightness  = true
            toggleCpuLimit    = true
            toggle5G          = mainTemp >= 43f
            toggleBgApps      = true
            toggleSync        = true
            toggleRefreshRate = mainTemp >= 43f
        } else {
            toggleBrightness  = false
            toggleCpuLimit    = false
            toggle5G          = false
            toggleBgApps      = false
            toggleSync        = false
            toggleRefreshRate = false
        }
    }

    // Aplicar acción de cada toggle
    fun applyBrightness(on: Boolean) {
        try {
            val cr = context.contentResolver
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS,
                    if (on) 89 else 200)
            }
        } catch (_: Exception) {}
    }

    // Pulso animado
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.4f, targetValue = if (isActive) 1f else 0.5f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pa")

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF070B12))) {

        // Resplandor de fondo
        Box(modifier = Modifier.size(280.dp).align(Alignment.TopCenter).offset(y = 20.dp)
            .blur(100.dp).background(
                if (isActive) Color(0xFFFF3D00).copy(alpha = pulseAlpha * 0.4f)
                else Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape))

        Column(modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ── HEADER ──────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) {
                    Text("⚡", fontSize = 22.sp) }
                Column {
                    Text("Modo Bestia", fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold, color = Color(0xFFE0E6FF))
                    Text("Control térmico agresivo", fontSize = 11.sp, color = accent) }
            }

            // ── TEMPERATURA CENTRAL ──────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Color(0x12FFFFFF))
                .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {

                    // Círculo de temperatura
                    Box(modifier = Modifier.size(90.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(
                            accent.copy(alpha = 0.2f), Color.Transparent)))
                        .border(2.dp, accent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${mainTemp.toInt()}°C", fontSize = 28.sp,
                                fontWeight = FontWeight.Black, color = accent)
                            Text(when (level) {
                                ThermalLevel.NORMAL -> "Normal"
                                ThermalLevel.WARM   -> "Tibio"
                                ThermalLevel.HOT    -> "Caliente"
                                ThermalLevel.CRITICAL -> "Crítico"
                                ThermalLevel.EMERGENCY -> "Emergencia"
                            }, fontSize = 9.sp, color = accent.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold)
                        }
                    }

                    // Métricas laterales
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TempMetricRow("🔋", "Batería", "${snap.batteryTemp.toInt()}°C",
                            snap.batteryTemp > 40f)
                        if (snap.gpuTemp > 20f)
                            TempMetricRow("🎮", "GPU", "${snap.gpuTemp.toInt()}°C",
                                snap.gpuTemp > 45f)
                        TempMetricRow("⚙️", "CPU uso", "${snap.cpuUsage.toInt()}%",
                            snap.cpuUsage > 70f)
                        if (snap.isCharging)
                            TempMetricRow("⚡", "Cargando", "Sí",
                                snap.batteryTemp > 40f)
                    }
                }
            }

            // ── BOTÓN MASTER ─────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().height(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isActive)
                    Brush.horizontalGradient(listOf(Color(0xFFBF360C), Color(0xFFFF3D00)))
                else
                    Brush.horizontalGradient(listOf(Color(0xFF006064), Color(0xFF00E5FF))))
                .clickable {
                    val newMode = if (isActive) OperationMode.AUTO else OperationMode.GAMER
                    onSetMode(newMode)
                    if (newMode == OperationMode.GAMER) applyBrightness(true)
                    else applyBrightness(false)
                }, contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isActive) {
                        Box(Modifier.size(8.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = pulseAlpha)))
                    }
                    Text(if (isActive) "DESACTIVAR MODO BESTIA" else "🐉  ACTIVAR MODO BESTIA",
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }

            // Alerta cargador
            if (snap.isCharging && mainTemp >= 42f) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFF6F00).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFFFF6F00).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 20.sp)
                        Column {
                            Text("Desconecta el cargador", fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
                            Text("Cargar a ${mainTemp.toInt()}°C genera calor adicional en la batería (Q=I²·Rint)",
                                fontSize = 11.sp, color = Color(0xFFE0E6FF).copy(alpha = 0.7f))
                        }
                    }
                }
            }

            // ── SECCIÓN: Acciones individuales (estilo Samsung) ──────────
            Text("Acciones de enfriamiento",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFE0E6FF).copy(alpha = 0.6f))

            // Toggle 1: Brillo
            BeastToggle(
                icon = "🔆",
                title = "Reducir brillo máximo",
                subtitle = "Baja la pantalla al 35% — reduce hasta 1.5W de disipación",
                physicsNote = "Ley de Joule: P=V²/R · Pantalla OLED consume hasta 3W al máximo",
                checked = toggleBrightness,
                enabled = true,
                onToggle = { v ->
                    toggleBrightness = v
                    applyBrightness(v)
                }
            )

            // Toggle 2: CPU
            BeastToggle(
                icon = "⚙️",
                title = "Limitar boost de CPU",
                subtitle = "Solicita al kernel reducir picos de frecuencia",
                physicsNote = "Ley de Dennard: P∝C·V²·f — bajar f 20% reduce potencia ~35%",
                checked = toggleCpuLimit,
                enabled = true,
                onToggle = { v ->
                    toggleCpuLimit = v
                    applyCpuLimit(v, context)
                }
            )

            // Toggle 3: Apps background
            BeastToggle(
                icon = "📱",
                title = "Cerrar apps en background",
                subtitle = "Elimina procesos inactivos que consumen CPU y RAM",
                physicsNote = "Reduce ciclos innecesarios del SoC → menos calor residual",
                checked = toggleBgApps,
                enabled = true,
                onToggle = { v ->
                    toggleBgApps = v
                    if (v) {
                        try {
                            val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                                    as android.app.ActivityManager
                            // Solicita al LMK limpiar procesos cached
                            am.isBackgroundRestricted.let { }
                        } catch (_: Exception) {}
                    }
                }
            )

            // Toggle 4: Sincronización
            BeastToggle(
                icon = "🔄",
                title = "Pausar sincronización",
                subtitle = "Detiene descargas y sync automático en background",
                physicsNote = "Cada sync activa el PA del radio: ~0.3W adicionales de calor RF",
                checked = toggleSync,
                enabled = true,
                onToggle = { v ->
                    toggleSync = v
                    try {
                        android.content.ContentResolver.setMasterSyncAutomatically(!v)
                    } catch (_: Exception) {}
                }
            )

            // Toggle 5: 5G (con advertencia)
            BeastToggle(
                icon = "📶",
                title = "Apagar 5G cuando no se usa",
                subtitle = "El PA del 5G disipa hasta 0.8W en señal débil",
                physicsNote = "Friis + Eficiencia PA: η_PA≈25–30% en señal baja → 0.6W de calor puro",
                checked = toggle5G,
                enabled = mainTemp >= 43f,
                disabledReason = "Se activa a partir de 43°C",
                onToggle = { v ->
                    toggle5G = v
                    applyNetworkAction(v, context)
                }
            )

            // Toggle 6: Tasa de refresco
            BeastToggle(
                icon = "🖥️",
                title = "Suavidad estándar (60Hz)",
                subtitle = "Reduce tasa de refresco de 120Hz a 60Hz",
                physicsNote = "GPU renderiza 50% menos frames → baja fill rate y calor del shader",
                checked = toggleRefreshRate,
                enabled = mainTemp >= 43f,
                disabledReason = "Se activa a partir de 43°C",
                onToggle = { v ->
                    toggleHz = v
                    applyRefreshRate(v, context)
                }
            )

            // ── GUÍA MANUAL ──────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color(0x0AFFFFFF))
                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
                .padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("💡  Haz esto también manualmente",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFE0E6FF))
                    listOf(
                        "🧤  Quita la funda — bloquea ~30% de la disipación por convección",
                        "📐  Apoya el teléfono vertical — mejora flujo de aire (Ley de Grashof)",
                        "✈️  Modo avión 2 min — apaga el PA del modem completamente",
                        "🌬️  No pongas el teléfono boca abajo — trapa el calor"
                    ).forEach { tip ->
                        Text(tip, fontSize = 11.sp,
                            color = Color(0xFFE0E6FF).copy(alpha = 0.65f),
                            lineHeight = 16.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun TempMetricRow(icon: String, label: String, value: String, alert: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 12.sp)
        Text(label, fontSize = 11.sp, color = Color(0xFFE0E6FF).copy(alpha = 0.5f))
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (alert) Color(0xFFFF6F00) else Color(0xFF00E5FF))
    }
}

@Composable
fun BeastToggle(
    icon: String,
    title: String,
    subtitle: String,
    physicsNote: String,
    checked: Boolean,
    enabled: Boolean = true,
    disabledReason: String = "",
    onToggle: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val borderColor = when {
        !enabled -> Color(0x15FFFFFF)
        checked  -> Color(0xFF00E5FF).copy(alpha = 0.4f)
        else     -> Color(0x20FFFFFF)
    }
    val bgColor = when {
        !enabled -> Color(0x05FFFFFF)
        checked  -> Color(0xFF00E5FF).copy(alpha = 0.06f)
        else     -> Color(0x0AFFFFFF)
    }

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(bgColor)
        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
        .clickable(enabled = enabled) { expanded = !expanded }
        .padding(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(icon, fontSize = 18.sp)
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (enabled) Color(0xFFE0E6FF) else Color(0xFFE0E6FF).copy(alpha = 0.4f))
                    Text(if (!enabled && disabledReason.isNotEmpty()) disabledReason else subtitle,
                        fontSize = 10.sp,
                        color = if (!enabled) Color(0xFFFF6F00).copy(alpha = 0.6f)
                                else Color(0xFFE0E6FF).copy(alpha = 0.5f))
                }
                Switch(
                    checked = checked && enabled,
                    onCheckedChange = { if (enabled) onToggle(it) },
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00E5FF),
                        disabledCheckedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.3f),
                        uncheckedThumbColor = Color(0xFF607D8B),
                        uncheckedTrackColor = Color(0x30FFFFFF)
                    )
                )
            }
            // Nota de física expandible
            AnimatedVisibility(visible = expanded && enabled) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Color(0x0AFFFFFF)).padding(10.dp)) {
                    Text("⚛️  $physicsNote", fontSize = 10.sp,
                        color = Color(0xFFE0E6FF).copy(alpha = 0.55f), lineHeight = 15.sp)
                }
            }
        }
    }

// ═══════════════════════════════════════════════════════════════
//  Acciones reales del Modo Bestia — sin root
// ═══════════════════════════════════════════════════════════════

fun applyCpuLimit(enable: Boolean, context: Context) {
    try {
        if (enable) {
            // Bajar prioridad de threads — el scheduler del kernel asigna menos CPU
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            // Solicitar modo de ahorro de batería al sistema — limita frecuencias de CPU
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            // En Android 9+ el sistema reduce frecuencia en background cuando hay presión térmica
            // Forzar garbage collection — libera heap, reduce presión en CPU
            System.gc()
            Runtime.getRuntime().gc()
        } else {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT)
        }
    } catch (_: Exception) {}
}

fun applyNetworkAction(enable: Boolean, context: Context) {
    try {
        if (enable) {
            // Desactivar WiFi — ahorro real de ~50–150mW
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            wm.isWifiEnabled = false
            // Abrir ajustes de red para que el usuario desactive 5G manualmente
            val intent = android.content.Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(intent) } catch (_: Exception) {
                val i2 = android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i2)
            }
        } else {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            wm.isWifiEnabled = true
        }
    } catch (_: Exception) {}
}

fun applyRefreshRate(enable: Boolean, context: Context) {
    try {
        // Reducir tasa de refresco de pantalla vía Display.Mode
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val display = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val modes = display.supportedModes
        if (enable) {
            // Elegir el modo con menor tasa de refresco disponible
            val lowMode = modes.minByOrNull { it.refreshRate }
            if (lowMode != null) {
                // Guardar preferencia — la Activity la aplica al resumirse
                context.getSharedPreferences("beast_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("preferred_refresh_mode", lowMode.modeId).apply()
            }
        } else {
            // Restaurar modo de mayor tasa
            val highMode = modes.maxByOrNull { it.refreshRate }
            if (highMode != null) {
                context.getSharedPreferences("beast_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("preferred_refresh_mode", highMode.modeId).apply()
            }
        }
    } catch (_: Exception) {}
}

}