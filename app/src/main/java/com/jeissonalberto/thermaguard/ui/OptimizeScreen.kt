package com.jeissonalberto.thermaguard.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.ThermalUiState

@Composable
fun OptimizeScreen(
    uiState: ThermalUiState,
    onSetMode: (OperationMode) -> Unit = {}
) {
    val snap   = uiState.latest
    val mainTemp = if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp
    val level  = mainTemp.toThermalLevel()
    val accent = TG.accentFor(level)
    val scroll = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(TG.bg)) {
        Box(modifier = Modifier.size(300.dp).align(Alignment.TopEnd).offset(x=60.dp,y=(-60).dp)
            .blur(80.dp).background(accent.copy(alpha=0.1f), androidx.compose.foundation.shape.CircleShape))

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Tune, null, tint = accent, modifier = Modifier.size(22.dp))
                Column {
                    Text("Optimizar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TG.textPri)
                    Text("Control del motor termico", fontSize = 11.sp, color = TG.textDim)
                }
            }

            // Estado actual
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TG.glass)
                    .border(1.dp, accent.copy(alpha=0.2f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Estado actual", fontSize = 10.sp, color = TG.textDim)
                    Text("${mainTemp.toInt()}°C", fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold, color = accent)
                    Text(when(level) {
                        ThermalLevel.NORMAL    -> "Todo bajo control"
                        ThermalLevel.WARM      -> "Calentando un poco"
                        ThermalLevel.HOT       -> "Temperatura alta"
                        ThermalLevel.CRITICAL  -> "Temperatura critica"
                        ThermalLevel.EMERGENCY -> "Emergencia termica"
                    }, fontSize = 11.sp, color = TG.textSec)
                }
                Box(
                    modifier = Modifier.size(64.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(accent.copy(alpha=0.1f))
                        .border(2.dp, accent.copy(alpha=0.3f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${uiState.profile?.riskScore ?: 0}", fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, color = accent)
                }
            }

            // Selector de modo
            Text("Modo de operacion", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = TG.textPri)
            ModeSelector(mode = uiState.operationMode, onSetMode = onSetMode, accent = accent)

            // Recomendaciones
            if (uiState.coolingRecs.isNotEmpty()) {
                Text("Acciones recomendadas", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = TG.textPri)
                CoolingRecsCard(recs = uiState.coolingRecs)
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TG.glass)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("OK", fontSize = 32.sp)
                        Text("Sin recomendaciones", fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, color = TG.textPri)
                        Text("El dispositivo esta funcionando correctamente", fontSize = 11.sp, color = TG.textDim)
                    }
                }
            }

            // Tips
            val tips = uiState.smartTips.take(3)
            if (tips.isNotEmpty()) {
                Text("Consejos inteligentes", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = TG.textPri)
                SmartTipsCard(tips = tips)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
