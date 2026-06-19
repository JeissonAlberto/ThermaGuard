package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.ThermalUiState
import kotlinx.coroutines.delay

@Composable
fun OptimizeScreen(
    uiState: ThermalUiState,
    onSetMode: (OperationMode) -> Unit = {},
    onKillApps: () -> Unit = {}
) {
    val snap     = uiState.latest
    val mainTemp = if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp
    val level    = mainTemp.toThermalLevel()
    val accent   = TG.accentFor(level)
    val scroll   = rememberScrollState()

    var killFeedback by remember { mutableStateOf(false) }
    var isKilling    by remember { mutableStateOf(false) }

    // Auto-ocultar feedback tras 3s
    LaunchedEffect(killFeedback) {
        if (killFeedback) { delay(3000); killFeedback = false }
    }

    Box(modifier = Modifier.fillMaxSize().background(TG.bg)) {
        Box(
            modifier = Modifier.size(300.dp).align(Alignment.TopEnd).offset(x = 60.dp, y = (-60).dp)
                .background(accent.copy(alpha = 0.08f), androidx.compose.foundation.shape.CircleShape)
        )

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Tune, null, tint = accent, modifier = Modifier.size(22.dp))
                Column {
                    Text("Optimizar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TG.textPri)
                    Text("Control del motor térmico", fontSize = 11.sp, color = TG.textDim)
                }
            }

            // ── Estado actual ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TG.glass)
                    .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Estado actual", fontSize = 10.sp, color = TG.textDim)
                    Text("${mainTemp.toInt()}°C", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = accent)
                    Text(
                        when (level) {
                            ThermalLevel.NORMAL    -> "Todo bajo control"
                            ThermalLevel.WARM      -> "Calentando un poco"
                            ThermalLevel.HOT       -> "Temperatura alta"
                            ThermalLevel.CRITICAL  -> "Temperatura crítica"
                            ThermalLevel.EMERGENCY -> "Emergencia térmica"
                        }, fontSize = 11.sp, color = TG.textSec
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier.size(56.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(accent.copy(alpha = 0.12f))
                            .border(2.dp, accent.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${uiState.profile?.riskScore ?: 0}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = accent)
                    }
                    Text("Riesgo", fontSize = 9.sp, color = TG.textDim)
                }
            }

            // ── Botón Limpiar Ahora ───────────────────────────────────────
            Button(
                onClick = {
                    if (!isKilling) {
                        isKilling = true
                        onKillApps()
                        killFeedback = true
                        isKilling = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (level == ThermalLevel.NORMAL) TG.glass else accent.copy(alpha = 0.85f),
                    contentColor   = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                enabled = !isKilling
            ) {
                Icon(
                    if (isKilling) Icons.Default.HourglassTop else Icons.Default.CleaningServices,
                    null, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isKilling) "Limpiando…" else "Limpiar apps ahora",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
            }

            // Feedback de limpieza
            AnimatedVisibility(visible = killFeedback, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TG.green.copy(alpha = 0.1f))
                        .border(1.dp, TG.green.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("✅", fontSize = 16.sp)
                    Column {
                        Text("Apps cerradas y RAM liberada", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TG.green)
                        Text("El motor automático registró la acción", fontSize = 10.sp, color = TG.textDim)
                    }
                }
            }

            // ── Selector de modo ──────────────────────────────────────────
            Text("Modo de operación", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
            ModeSelector(mode = uiState.operationMode, onSetMode = onSetMode, accent = accent)

            // ── Recomendaciones ────────────────────────────────────────────
            val effectiveRecs = uiState.coolingRecs.ifEmpty {
                when {
                    mainTemp >= 55f -> listOf(
                        CoolingRecommendation("opt1", CoolingCategory.BACKGROUND, "Cierra apps en segundo plano",
                            "Hay procesos activos elevando la temperatura.", 3f, 1, true, "📱"),
                        CoolingRecommendation("opt2", CoolingCategory.PERFORMANCE, "Reduce el brillo",
                            "La pantalla al máximo genera calor adicional.", 1.5f, 1, true, "☀️"),
                        CoolingRecommendation("opt3", CoolingCategory.ENVIRONMENT, "Deja el teléfono en reposo",
                            "Pausa el uso intensivo unos minutos.", 4f, 1, true, "⏸️")
                    )
                    mainTemp >= 43f -> listOf(
                        CoolingRecommendation("opt1", CoolingCategory.BACKGROUND, "Revisa apps con alto consumo",
                            "Algunos procesos usan CPU de forma sostenida.", 2f, 1, true, "📊"),
                        CoolingRecommendation("opt2", CoolingCategory.PERFORMANCE, "Modo Automático recomendado",
                            "El motor puede gestionar esto de forma autónoma.", 1.5f, 1, true, "⚙️")
                    )
                    mainTemp >= 38f -> listOf(
                        CoolingRecommendation("opt1", CoolingCategory.ENVIRONMENT, "Temperatura leve — sigue monitoreando",
                            "El dispositivo está tibio pero dentro del rango normal.", 0f, 1, false, "🌡️")
                    )
                    else -> emptyList()
                }
            }

            if (effectiveRecs.isNotEmpty()) {
                Text("Acciones recomendadas", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                CoolingRecsCard(recs = effectiveRecs)
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TG.green.copy(alpha = 0.07f))
                        .border(1.dp, TG.green.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✅", fontSize = 32.sp)
                        Text("Todo en orden", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TG.green)
                        Text("${mainTemp.toInt()}°C — temperatura normal, sin acción necesaria",
                            fontSize = 11.sp, color = TG.textDim, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }

            // ── Tips inteligentes ─────────────────────────────────────────
            val tips = uiState.smartTips.take(3)
            if (tips.isNotEmpty()) {
                Text("Consejos inteligentes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                SmartTipsCard(tips = tips)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
