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
fun DiagnosisScreen(uiState: ThermalUiState) {
    val scroll = rememberScrollState()
    val snap   = uiState.latest
    val diags  = uiState.componentDiagnoses

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF1C1C2E), Color(0xFF0F2027))))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Science, null, tint = Color(0xFFFF8A65), modifier = Modifier.size(24.dp))
                Column {
                    Text("Diagnostico", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Analisis de componentes en tiempo real", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }

            // Resumen rápido de zonas
            ZonesSummaryCard(snap = snap)

            // Per-core CPU
            if (snap.perCoreUsage.isNotEmpty()) {
                PerCoreCard(cores = snap.perCoreUsage)
            }

            // Diagnostico por componente
            if (diags.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = Color(0xFF80CBC4), modifier = Modifier.size(36.dp))
                        Text("Analizando componentes...", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            } else {
                Text("Componentes detectados", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f))
                diags.forEach { diag ->
                    ComponentDiagnosisCard(diag = diag)
                }
            }

            // Procesos activos
            if (snap.topProcesses.isNotEmpty()) {
                ProcessListCard(processes = snap.topProcesses)
            }

            // RAM
            if (snap.ramUsageMb > 0) {
                RamCard(usedMb = snap.ramUsageMb)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ZonesSummaryCard(snap: ThermalSnapshot) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DeviceThermostat, null, tint = Color(0xFFFF8A65), modifier = Modifier.size(18.dp))
                Text("Zonas termicas", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            val zones = listOfNotNull(
                if (snap.batteryTemp > 0f) Pair("🔋 Bateria",  snap.batteryTemp) else null,
                if (snap.cpuTemp     > 0f) Pair("💻 CPU",      snap.cpuTemp)     else null,
                if (snap.gpuTemp     > 0f) Pair("🎮 GPU",      snap.gpuTemp)     else null,
                if (snap.skinTemp    > 0f) Pair("📱 Superficie",snap.skinTemp)    else null,
                if (snap.boardTemp   > 0f) Pair("🔧 Placa",    snap.boardTemp)    else null,
                if (snap.modemTemp   > 0f) Pair("📡 Modem",    snap.modemTemp)    else null,
                if (snap.displayTemp > 0f) Pair("🖥️ Display",  snap.displayTemp)  else null,
            ).sortedByDescending { it.second }

            if (zones.isEmpty()) {
                Text("No se detectaron zonas termicas en este dispositivo.",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
            } else {
                zones.forEach { (label, temp) ->
                    ZoneRow(label = label, temp = temp)
                }
            }
        }
    }
}

@Composable
fun ZoneRow(label: String, temp: Float) {
    val tempColor = when {
        temp >= 50f -> Color(0xFFFF1744)
        temp >= 45f -> Color(0xFFFF6D00)
        temp >= 40f -> Color(0xFFFFD600)
        temp >= 35f -> Color(0xFFFFEE58)
        else        -> Color(0xFF00E676)
    }
    val progress = (temp / 60f).coerceIn(0f, 1f)
    val animProg by animateFloatAsState(targetValue = progress, animationSpec = tween(600), label = "z")

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            Text("${temp.toInt()}°C", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tempColor)
        }
        Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.08f))) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(animProg)
                .background(Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFFFFD600), Color(0xFFFF6D00), Color(0xFFFF1744))))
                .clip(RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
fun PerCoreCard(cores: List<Float>) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Memory, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                Text("Nucleos CPU (${cores.size} nucleos)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            // Grid de cores
            val cols = if (cores.size <= 4) 2 else 4
            val rows = (cores.size + cols - 1) / cols
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until cols) {
                            val idx = row * cols + col
                            if (idx < cores.size) {
                                CoreChip(modifier = Modifier.weight(1f), coreIndex = idx, usage = cores[idx])
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoreChip(modifier: Modifier, coreIndex: Int, usage: Float) {
    val color = when {
        usage >= 80f -> Color(0xFFFF1744)
        usage >= 60f -> Color(0xFFFF6D00)
        usage >= 30f -> Color(0xFFFFD600)
        else         -> Color(0xFF00E676)
    }
    val animUsage by animateFloatAsState(targetValue = usage, animationSpec = tween(500), label = "core$coreIndex")

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.1f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("Core $coreIndex", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
            Text("${animUsage.toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            LinearProgressIndicator(
                progress = animUsage / 100f,
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}

@Composable
fun ComponentDiagnosisCard(diag: ComponentDiagnosis) {
    var expanded by remember { mutableStateOf(diag.status != ComponentStatus.NORMAL) }

    val statusColor = Color(diag.status.color)
    val borderAlpha = if (diag.status == ComponentStatus.NORMAL) 0.15f else 0.5f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = statusColor.copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth().border(1.dp, statusColor.copy(alpha = borderAlpha), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)) {
                    // Icono componente
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(diag.component.icon, fontSize = 20.sp)
                    }
                    Column {
                        Text(diag.component.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (diag.temp > 0f) {
                                Text("${diag.temp.toInt()}°C", fontSize = 13.sp, color = statusColor, fontWeight = FontWeight.Bold)
                            }
                            if (diag.usagePct >= 0f) {
                                Text("·", color = Color.White.copy(alpha = 0.3f))
                                Text("${diag.usagePct.toInt()}%", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                        Text(diag.status.label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Medium)
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Divider(color = Color.White.copy(alpha = 0.08f))

                    // Causa
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Search, null, tint = Color(0xFFFFCC80), modifier = Modifier.size(15.dp).padding(top = 1.dp))
                        Column {
                            Text("Causa", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Medium)
                            Text(diag.cause, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 18.sp)
                        }
                    }

                    // Consejo
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Lightbulb, null, tint = Color(0xFF80CBC4), modifier = Modifier.size(15.dp).padding(top = 1.dp))
                        Column {
                            Text("Que hacer", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Medium)
                            Text(diag.advice, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), lineHeight = 18.sp)
                        }
                    }

                    // Barra de temperatura del componente
                    if (diag.temp > 0f) {
                        val prog = (diag.temp / 60f).coerceIn(0f, 1f)
                        val animP by animateFloatAsState(targetValue = prog, animationSpec = tween(700), label = "p")
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("0°C", fontSize = 9.sp, color = Color.White.copy(alpha = 0.25f))
                                Text("30°C", fontSize = 9.sp, color = Color.White.copy(alpha = 0.25f))
                                Text("45°C", fontSize = 9.sp, color = Color.White.copy(alpha = 0.25f))
                                Text("60°C", fontSize = 9.sp, color = Color.White.copy(alpha = 0.25f))
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.08f))) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(animP)
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFFFFD600), Color(0xFFFF6D00), Color(0xFFFF1744))))
                                    .clip(RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessListCard(processes: List<ProcessInfo>) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.List, null, tint = Color(0xFFCE93D8), modifier = Modifier.size(18.dp))
                Text("Procesos activos", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            processes.forEachIndexed { i, proc ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(if (i == 0) Color(0xFFFF8A65).copy(alpha=0.2f) else Color.White.copy(alpha=0.06f)),
                        contentAlignment = Alignment.Center) {
                        Text("${i+1}", fontSize = 12.sp, color = if (i == 0) Color(0xFFFF8A65) else Color.White.copy(alpha=0.4f),
                            fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(proc.name, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        Text(proc.description, fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                    Text("PID ${proc.pid}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
                }
                if (i < processes.size - 1) Divider(color = Color.White.copy(alpha = 0.05f))
            }
        }
    }
}

@Composable
fun RamCard(usedMb: Int) {
    val usedGb = usedMb / 1024f
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Storage, null, tint = Color(0xFF80CBC4), modifier = Modifier.size(18.dp))
                Column {
                    Text("RAM en uso", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Consumo de memoria RAM", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (usedGb >= 1f) "${"%.1f".format(usedGb)} GB" else "$usedMb MB",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF80CBC4))
                Text("usado", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
            }
        }
    }
}
