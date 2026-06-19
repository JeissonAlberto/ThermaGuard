package com.jeissonalberto.thermaguard.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.ThermalUiState

/**
 * PhysicsScreen — Motor de Física del Silicio v2.0
 *
 * Muestra el análisis completo de las 19 leyes físicas integradas:
 *  • Termodinámica: Fourier · Newton · Stefan-Boltzmann · Fick · Wiedemann-Franz
 *  • VLSI: Moore · Dennard · Pollack · Amdahl · Arrhenius (NBTI)
 *  • Circuitos: Ohm Térmico · RC · Joule · Capacitancia Parásita
 *  • Android: Thermal HAL · Governor · GPU DVFS · EAS · Throttling
 */
@Composable
fun PhysicsScreen(
    uiState: ThermalUiState,
    onApplyGovernor: () -> Unit = {},
    onEmergencyThrottle: () -> Unit = {},
    onUnlockMaxPerf: () -> Unit = {}
) {
    val snap     = uiState.latest
    val physics  = uiState.physicsAnalysis
    val govLog   = uiState.governorLog
    val mainTemp = if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp
    val level    by remember(mainTemp) { derivedStateOf { mainTemp.toThermalLevel() } }
    val accent   by remember(level) { derivedStateOf { TG.accentFor(level) } }
    val scroll   = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(TG.bg)) {

        // Orb de fondo
        Box(modifier = Modifier.size(320.dp).align(Alignment.TopEnd).offset(x=60.dp, y=(-40).dp)
            .blur(90.dp).background(accent.copy(alpha=0.08f), CircleShape))

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── HEADER ────────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Motor de Física", fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold, color = TG.textPri,
                        letterSpacing = (-0.8).sp)
                    Text("19 leyes · Silicon Physics Engine v2.0",
                        fontSize = 11.sp, color = TG.textSec)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha=0.12f))
                    .border(1.dp, accent.copy(alpha=0.25f), RoundedCornerShape(12.dp))
                    .padding(horizontal=12.dp, vertical=6.dp)) {
                    Text("${mainTemp.toInt()}°C", fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold, color = accent)
                }
            }

            if (physics == null) {
                // Estado sin datos
                Box(modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = accent)
                        Text("Calculando análisis de física...",
                            fontSize = 13.sp, color = TG.textSec)
                        OutlinedButton(onClick = onApplyGovernor) {
                            Text("Iniciar análisis", color = accent)
                        }
                    }
                }
                return@Column
            }

            // ── TERMODINÁMICA ────────────────────────────────────────────────
            PhysicsSection(title = "Termodinámica", icon = Icons.Default.Thermostat, accent = accent) {
                PhysicsRow("🔴 Fourier (Conducción)",
                    "${"%.1f".format(physics.conductionFlux_W)} W",
                    "Flujo calor die → carcasa")
                PhysicsRow("🔵 Newton (Convección)",
                    "${"%.2f".format(physics.convectionLoss_W)} W",
                    "Pérdida por superficie al aire")
                PhysicsRow("🟡 Stefan-Boltzmann (Radiación)",
                    "${"%.3f".format(physics.radiationLoss_W)} W",
                    "Emisión IR vidrio trasero (ε=0.93)")
                PhysicsRow(
                    "⚖️ Calor neto",
                    (if (physics.netHeatAccumulation_W > 0)
                        "+${"%.2f".format(physics.netHeatAccumulation_W)} W ⬆️"
                    else
                        "${"%.2f".format(physics.netHeatAccumulation_W)} W ⬇️"),
                    if (physics.netHeatAccumulation_W > 0) "Acumulando calor" else "Disipando — estable",
                    valueColor = if (physics.netHeatAccumulation_W > 0.5) TG.red
                                 else if (physics.netHeatAccumulation_W > 0) TG.amber
                                 else TG.green
                )
            }

            // ── POTENCIA SoC ─────────────────────────────────────────────────
            PhysicsSection(title = "Potencia del SoC", icon = Icons.Default.Memory, accent = accent) {
                PhysicsRow("⚡ Moore/CMOS (Dinámica)",
                    "${"%.2f".format(physics.dynamicPower_W)} W",
                    "α·C·V²·f  (α=0.3, V=0.85V)")
                PhysicsRow("💧 Dennard (Fuga)",
                    "${"%.2f".format(physics.leakagePower_W)} W",
                    "Corriente de fuga × e^(ΔT/12°C)")
                PhysicsRow("🔥 Total SoC",
                    "${"%.2f".format(physics.totalSocPower_W)} W",
                    "de ${PhysicsConst.TDP_SNAPDRAGON} W TDP máximo",
                    valueColor = when {
                        physics.totalSocPower_W >= 9.0 -> TG.red
                        physics.totalSocPower_W >= 6.0 -> TG.amber
                        else -> TG.green
                    })
                PhysicsRow("🔌 Joule PMIC",
                    "${"%.3f".format(physics.joulePmicHeat_W)} W",
                    "I²·R en inductores del regulador")
            }

            // ── RENDIMIENTO Y DEGRADACIÓN ─────────────────────────────────────
            PhysicsSection(title = "Rendimiento y Degradación", icon = Icons.Default.Speed, accent = accent) {
                PhysicsRow("📉 Amdahl (Throttle CPU)",
                    if (physics.throttlePct > 0) "-${physics.throttlePct}%" else "Sin throttle",
                    "Pérdida throughput por limitación térmica",
                    valueColor = when {
                        physics.throttlePct >= 30 -> TG.red
                        physics.throttlePct >= 10 -> TG.amber
                        else -> TG.green
                    })
                PhysicsRow("🚀 Pollack (Rendimiento real)",
                    "${"%.1f".format(physics.performanceRatio * 100)}%",
                    "Rendimiento relativo al pico (√P)")
                PhysicsRow("🧬 Arrhenius (MTTF)",
                    "${"%.0f".format(physics.mttfHours / 1000)}k h",
                    "Vida útil estimada a temperatura actual",
                    valueColor = when {
                        physics.mttfHours < 20_000 -> TG.red
                        physics.mttfHours < 50_000 -> TG.amber
                        else -> TG.green
                    })
                // Barra degradación
                val degPct = (physics.degradationIndex * 100).toInt()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier=Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("Índice degradación (NBTI/HCI)",
                            fontSize=11.sp, color=TG.textSec)
                        Text("$degPct%", fontSize=11.sp,
                            color=if(degPct>50) TG.red else TG.green,
                            fontWeight=FontWeight.Bold)
                    }
                    Box(modifier=Modifier.fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(3.dp)).background(TG.glass)) {
                        Box(modifier=Modifier.fillMaxHeight()
                            .fillMaxWidth(physics.degradationIndex.toFloat().coerceIn(0f,1f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(TG.green,TG.amber,TG.red))))
                    }
                }
            }

            // ── CIRCUITOS ELECTRÓNICOS ────────────────────────────────────────
            PhysicsSection(title = "Circuitos Electrónicos", icon = Icons.Default.ElectricBolt, accent = accent) {
                PhysicsRow("🔗 Ohm Térmico (θ_ja)",
                    "${"%.1f".format(physics.thermalResistance_KW)} K/W",
                    "Resistencia térmica junction→ambiente")
                PhysicsRow("⏱ RC Térmico (τ)",
                    "${"%.0f".format(physics.thermalTimeConst_s)} s",
                    "Tiempo a equilibrio térmico del die")
                PhysicsRow("🎮 Throttle GPU",
                    if (physics.gpuThrottlePct > 0) "-${physics.gpuThrottlePct}%" else "Sin throttle",
                    "Adreno 730 / Xclipse 920",
                    valueColor = when {
                        physics.gpuThrottlePct >= 40 -> TG.red
                        physics.gpuThrottlePct >= 15 -> TG.amber
                        else -> TG.green
                    })
            }

            // ── GOVERNOR ANDROID ──────────────────────────────────────────────
            PhysicsSection(title = "Governor Android / Kernel", icon = Icons.Default.Tune, accent = accent) {
                PhysicsRow("🖥 CPU Governor",
                    physics.recommendedGovernor,
                    "Recomendado por SiliconPhysicsEngine")
                PhysicsRow("📡 Freq máx CPU",
                    "${"%.2f".format(physics.recommendedMaxFreqGHz)} GHz",
                    "de 3.18 GHz peak (prime Cortex-X2)")
                PhysicsRow("🎯 Afinidad CPU",
                    "0x${physics.cpuAffinityMask.toString(16).uppercase()}",
                    "Máscara de cores (big.LITTLE S22)")

                // Botones de acción
                Spacer(Modifier.height(4.dp))
                Row(modifier=Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onApplyGovernor,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, accent.copy(alpha=0.4f))
                    ) {
                        Text("Aplicar óptimo", fontSize=11.sp, color=accent)
                    }
                    OutlinedButton(
                        onClick = onUnlockMaxPerf,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, TG.red.copy(alpha=0.4f))
                    ) {
                        Text("🐉 Máx perf", fontSize=11.sp, color=TG.red)
                    }
                }
                // Log de governor
                if (govLog.isNotEmpty()) {
                    Column(modifier=Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x0AFFFFFF))
                        .padding(10.dp),
                        verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text("Log de acciones",
                            fontSize=9.sp, color=TG.textDim,
                            fontWeight=FontWeight.SemiBold)
                        govLog.forEach { line ->
                            Text(line, fontSize=10.sp, color=TG.textSec)
                        }
                    }
                }
            }

            // ── RESUMEN ───────────────────────────────────────────────────────
            if (physics.summaryLines.isNotEmpty()) {
                PhysicsSection(title = "Resumen del análisis", icon = Icons.Default.Analytics, accent = accent) {
                    physics.summaryLines.forEach { line ->
                        Text(line, fontSize=11.sp, color=TG.textSec,
                            modifier=Modifier.padding(vertical=1.dp))
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─── Componentes privados ─────────────────────────────────────────────────────
@Composable
private fun PhysicsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(TG.glass)
        .border(1.dp, accent.copy(alpha=0.12f), RoundedCornerShape(18.dp))
        .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            Text(title, fontSize=13.sp, fontWeight=FontWeight.Bold, color=TG.textPri)
        }
        HorizontalDivider(color=accent.copy(alpha=0.1f))
        content()
    }
}

@Composable
private fun PhysicsRow(
    label: String,
    value: String,
    sub: String,
    valueColor: Color = TG.textPri
) {
    Row(modifier=Modifier.fillMaxWidth(),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.SpaceBetween) {
        Column(modifier=Modifier.weight(1f).padding(end=8.dp)) {
            Text(label, fontSize=11.sp, color=TG.textPri, fontWeight=FontWeight.Medium)
            Text(sub, fontSize=9.sp, color=TG.textDim)
        }
        Text(value, fontSize=14.sp, fontWeight=FontWeight.ExtraBold, color=valueColor)
    }
}
