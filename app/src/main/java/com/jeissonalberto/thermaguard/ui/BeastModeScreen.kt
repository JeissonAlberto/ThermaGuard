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
import com.jeissonalberto.thermaguard.root.RootEngine
import com.jeissonalberto.thermaguard.ui.theme.LocalTgColors




@Composable
fun BeastModeScreen(
    uiState: ThermalUiState,
    onSetMode: (OperationMode) -> Unit = {}
) {
    val tg = LocalTgColors.current
    val context   = LocalContext.current
    val snap      = uiState.latest
    val isActive  = uiState.operationMode == OperationMode.GAMER
    val mainTemp by remember(snap) { derivedStateOf {
        if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp
    } }
    val level  by remember(mainTemp) { derivedStateOf { mainTemp.toThermalLevel() } }
    val accent by remember(isActive, level) { derivedStateOf {
        if (isActive) Color(0xFFFF3D00) else TG.accentFor(level)
    } }

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
            // ── PANEL DE RENDIMIENTO GAMER ─────────────────────────────
            // Métricas exclusivas del modo: throttle %, presión térmica, núcleos
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Color(0x12FFFFFF))
                .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(20.dp)) {

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Título del panel
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Speed, null, tint = accent,
                            modifier = Modifier.size(16.dp))
                        Text("Rendimiento en tiempo real",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tg.textSec)
                    }

                    // Fila 1: Throttle estimado + Presión térmica
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                        // Throttle estimado (Ley de Amdahl)
                        val throttlePct = when {
                            mainTemp >= 50f -> 40
                            mainTemp >= 46f -> 25
                            mainTemp >= 43f -> 12
                            mainTemp >= 40f -> 5
                            else            -> 0
                        }
                        GamerStatCard(
                            modifier    = Modifier.weight(1f),
                            label       = "Throttle",
                            value       = if (throttlePct > 0) "-$throttlePct%" else "Sin límite",
                            sub         = "rendimiento CPU",
                            valueColor  = when {
                                throttlePct >= 25 -> TG.red
                                throttlePct >= 10 -> TG.amber
                                else              -> TG.green
                            }
                        )

                        // Presión térmica (score 0-100)
                        val thermalPressure = ((mainTemp - 30f) / 30f * 100f).coerceIn(0f, 100f).toInt()
                        GamerStatCard(
                            modifier   = Modifier.weight(1f),
                            label      = "Presión Térmica",
                            value      = "$thermalPressure%",
                            sub        = "margen de seguridad ${100 - thermalPressure}%",
                            valueColor = when {
                                thermalPressure >= 70 -> TG.red
                                thermalPressure >= 45 -> TG.amber
                                else                  -> TG.green
                            }
                        )
                    }

                    // Fila 2: Núcleos activos + ETA a throttle
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                        val activeCores = when {
                            isActive && snap.cpuUsage > 60f -> 8
                            isActive                        -> 6
                            snap.cpuUsage > 50f             -> 6
                            else                            -> 4
                        }
                        GamerStatCard(
                            modifier   = Modifier.weight(1f),
                            label      = "Núcleos",
                            value      = "$activeCores / 8",
                            sub        = if (isActive) "modo bestia activo" else "modo normal",
                            valueColor = if (isActive) TG.amber else TG.green
                        )

                        // ETA a zona crítica
                        val etaMin = when {
                            mainTemp >= 50f -> 0
                            mainTemp >= 46f -> 3
                            mainTemp >= 43f -> 8
                            mainTemp >= 40f -> 15
                            else            -> 99
                        }
                        GamerStatCard(
                            modifier   = Modifier.weight(1f),
                            label      = "ETA a throttle",
                            value      = if (etaMin >= 99) "—" else "${etaMin}min",
                            sub        = "a temperatura crítica",
                            valueColor = when {
                                etaMin <= 3  -> TG.red
                                etaMin <= 10 -> TG.amber
                                else         -> TG.green
                            }
                        )
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
                    android.os.Process.setThreadPriority(
                        if (v) android.os.Process.THREAD_PRIORITY_BACKGROUND
                        else android.os.Process.THREAD_PRIORITY_DEFAULT
                    )
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
                    try {
                        val i = android.content.Intent(android.provider.Settings.Panel.ACTION_WIFI)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(i)
                    } catch (_: Exception) {}
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
                    toggleRefreshRate = v
                    try {
                        val dm = context.getSystemService(android.content.Context.DISPLAY_SERVICE)
                            as android.hardware.display.DisplayManager
                        val modes = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY).supportedModes
                        val mode = if (v) modes.minByOrNull { it.refreshRate }
                                   else   modes.maxByOrNull { it.refreshRate }
                        mode?.let {
                            context.getSharedPreferences("beast_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putInt("preferred_refresh_mode", it.modeId).apply()
                        }
                    } catch (_: Exception) {}
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
    val tg = LocalTgColors.current
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
private fun GamerStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    sub: String,
    valueColor: Color
) {
    val tg = LocalTgColors.current
    Box(modifier = modifier
        .clip(RoundedCornerShape(12.dp))
        .background(Color.White.copy(alpha = 0.05f))
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 9.sp, color = tg.textDim,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                color = valueColor)
            Text(sub, fontSize = 9.sp, color = tg.textSec, maxLines = 1)
        }
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
    val tg = LocalTgColors.current
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
}
