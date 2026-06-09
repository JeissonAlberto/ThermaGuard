package com.jeissonalberto.thermaguard.ui

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.*
import com.jeissonalberto.thermaguard.ui.theme.TG
import kotlinx.coroutines.delay

@Composable
fun BeastModeScreen(
    uiState: ThermalUiState,
    onActivate: (Boolean) -> Unit = {},
    onSetMode: (OperationMode) -> Unit = {}
) {
    val snap      = uiState.latest
    val isActive  = uiState.operationMode == OperationMode.GAMER
    val mainTemp  = if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp
    val level     = mainTemp.toThermalLevel()
    val accent    = if (isActive) Color(0xFFFF3D00) else Color(0xFF00E5FF)

    // Pulso animado del indicador de temperatura
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.3f, targetValue = if (isActive) 0.9f else 0.5f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "alpha"
    )
    val pulseScale by pulse.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )

    // Contador de intervenciones activas
    var interventionCount by remember { mutableIntStateOf(0) }
    var lastAction by remember { mutableStateOf("En espera...") }

    LaunchedEffect(isActive, mainTemp) {
        if (isActive && mainTemp >= 42f) {
            val actions = BeastCoolingEngine.getActiveInterventions(mainTemp, snap)
            interventionCount = actions.size
            lastAction = actions.firstOrNull() ?: "Monitoreando..."
        } else if (!isActive) {
            interventionCount = 0
            lastAction = "En espera..."
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(TG.bg)) {

        // Fondo: resplandor dinámico según estado
        Box(modifier = Modifier
            .size(320.dp)
            .align(Alignment.TopCenter)
            .offset(y = 30.dp)
            .blur(120.dp)
            .background(
                if (isActive) Color(0xFFFF3D00).copy(alpha = pulseAlpha * 0.5f)
                else Color(0xFF00E5FF).copy(alpha = 0.2f),
                CircleShape
            ))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── HEADER ─────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) {
                    Text("⚡", fontSize = 22.sp)
                }
                Column {
                    Text("Modo Bestia", fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold, color = TG.textPri)
                    Text("Enfriamiento agresivo máximo",
                        fontSize = 11.sp, color = accent)
                }
            }

            // ── TEMPERATURA CENTRAL ────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(TG.glass)
                .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .padding(24.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    Box(modifier = Modifier
                        .size(140.dp)
                        .scale(if (isActive) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(
                                accent.copy(alpha = 0.25f),
                                Color.Transparent
                            ))
                        )
                        .border(2.dp, accent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${mainTemp.toInt()}°C",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                color = accent)
                            Text(when (level) {
                                ThermalLevel.NORMAL    -> "NORMAL"
                                ThermalLevel.WARM      -> "TIBIO"
                                ThermalLevel.HOT       -> "CALIENTE"
                                ThermalLevel.CRITICAL  -> "CRÍTICO"
                                ThermalLevel.EMERGENCY -> "EMERGENCIA"
                            }, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                               color = accent.copy(alpha = 0.8f))
                        }
                    }

                    // Métricas secundarias
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MiniMetric("CPU", "${snap.cpuUsage.toInt()}%", accent)
                        MiniMetric("BAT", "${snap.batteryTemp.toInt()}°C", TG.textSec)
                        if (snap.gpuTemp > 20f) MiniMetric("GPU", "${snap.gpuTemp.toInt()}°C", TG.amber)
                    }
                }
            }

            // ── BOTÓN ACTIVAR ──────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isActive)
                        Brush.horizontalGradient(listOf(Color(0xFFBF360C), Color(0xFFFF3D00)))
                    else
                        Brush.horizontalGradient(listOf(Color(0xFF00838F), Color(0xFF00E5FF)))
                )
                .clickable {
                    val newMode = if (isActive) OperationMode.AUTO else OperationMode.GAMER
                    onSetMode(newMode)
                    onActivate(newMode == OperationMode.GAMER)
                },
                contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isActive) "⚡" else "🐉", fontSize = 22.sp)
                    Text(
                        if (isActive) "DESACTIVAR MODO BESTIA" else "ACTIVAR MODO BESTIA",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            // ── ESTADO ACTUAL ─────────────────────────────────────────────
            if (isActive) {
                Box(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFF3D00).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFFFF3D00).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape)
                                .background(Color(0xFFFF3D00).copy(alpha = pulseAlpha)))
                            Text("MODO BESTIA ACTIVO",
                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFF3D00))
                            Spacer(Modifier.weight(1f))
                            Text("$interventionCount acciones",
                                fontSize = 10.sp, color = TG.textDim)
                        }
                        Text(lastAction, fontSize = 12.sp, color = TG.textSec)
                    }
                }
            }

            // ── FÍSICA APLICADA — tarjetas explicativas ───────────────────
            Text("⚙️  Intervenciones activas",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TG.textSec)

            val tecnicas = listOf(
                BeastTech(
                    icon = "🔆", title = "Control de brillo adaptativo",
                    physics = "Ley de Joule: P=V²/R · La pantalla OLED consume hasta 40% de la energía total del dispositivo. Reducir brillo al 40% baja la disipación de calor de la pantalla ~15°C equivalentes en el SoC.",
                    action = "Reduce brillo al 35% automáticamente",
                    active = isActive && mainTemp >= 40f
                ),
                BeastTech(
                    icon = "📶", title = "Throttling de radio RF",
                    physics = "Ley de Friis + Termodinámica: el amplificador de potencia RF (PA) del módem disipa P=(1-η)·Pin. En señal débil el PA aumenta potencia hasta 23dBm, generando 0.8W de calor. Limitando el duty cycle del PA se reduce esa disipación.",
                    action = "Limita transferencia de datos en background",
                    active = isActive && mainTemp >= 43f
                ),
                BeastTech(
                    icon = "⚙️", title = "Reducción de carga de trabajo",
                    physics = "Ley de Dennard: P∝C·V²·f. Bajar la frecuencia del SoC 20% reduce el consumo dinámico ~35% (relación cúbica). El kernel usa cpufreq/thermal para escalar frecuencia antes del throttle hardware.",
                    action = "Cierra procesos de background no esenciales",
                    active = isActive && mainTemp >= 44f
                ),
                BeastTech(
                    icon = "🔋", title = "Pausa de carga inteligente",
                    physics = "Termodinámica de Ragone: durante la carga, la reacción electroquímica Li+ genera calor Q=I²·Rint. A 45°C la resistencia interna aumenta 30%, creando un ciclo positivo de calor. Pausar carga elimina esa fuente térmica.",
                    action = "Recomendación: desconectar cargador si >43°C",
                    active = isActive && snap.isCharging && mainTemp >= 43f
                ),
                BeastTech(
                    icon = "🌀", title = "Optimización de conductividad térmica",
                    physics = "Ley de Fourier: q=-k·A·(dT/dx). El calor fluye del SoC (fuente) a la carcasa (sumidero) a través de la pasta térmica y el copper heat pipe. Poner el dispositivo en posición vertical mejora la convección natural del aire ~8% (flujo de Grashof).",
                    action = "Guía: posición vertical, sin funda, superficie dura",
                    active = true
                ),
                BeastTech(
                    icon = "🧠", title = "Suspensión de procesos GPU",
                    physics = "Arquitectura TBDR (Tile-Based Deferred Rendering): la GPU móvil procesa en tiles de 16x16px. Reducir la resolución de render o la tasa de FPS baja el fill rate y con él el consumo de shader units — principales fuentes de calor en la GPU.",
                    action = "Cierra apps de video, juegos y cámara en background",
                    active = isActive && snap.gpuTemp > 30f
                )
            )
            tecnicas.forEach { t -> BeastTechCard(t, accent) }

            // ── GUÍA RÁPIDA FÍSICA ────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TG.glass)
                .border(1.dp, TG.glassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🌡️  Guía de enfriamiento manual",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TG.textPri)
                    listOf(
                        "1. Quita la funda — bloquea 30% de la disipación por convección",
                        "2. Pantalla hacia arriba — mejor flujo de calor por conducción al aire",
                        "3. Activa modo avión 2 min — el PA del 5G es la mayor fuente de calor radio",
                        "4. Desactiva sincronización automática — elimina I/O de red periódico",
                        "5. Reduce resolución de pantalla a FHD si tienes QHD — baja 20% de consumo GPU",
                        "6. Nunca cargues con funda — la resistencia térmica sube 40% con funda plástica"
                    ).forEach { tip ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tip, fontSize = 11.sp, color = TG.textSec,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

data class BeastTech(
    val icon: String, val title: String,
    val physics: String, val action: String,
    val active: Boolean
)

@Composable
fun BeastTechCard(t: BeastTech, accent: Color) {
    var expanded by remember { mutableStateOf(false) }
    val borderColor = if (t.active) accent.copy(alpha = 0.5f) else Color(0x22FFFFFF)
    val bgColor     = if (t.active) accent.copy(alpha = 0.07f) else Color(0x0AFFFFFF)

    Box(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(bgColor)
        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
        .clickable { expanded = !expanded }
        .padding(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t.icon, fontSize = 20.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(t.title, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = TG.textPri)
                    Text(t.action, fontSize = 11.sp, color = TG.textSec)
                }
                if (t.active) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = TG.textDim, modifier = Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Box(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x0AFFFFFF))
                    .padding(10.dp)) {
                    Text("⚛️  ${t.physics}",
                        fontSize = 11.sp, color = TG.textSec, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun MiniMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 9.sp, color = TG.textDim)
    }
}
