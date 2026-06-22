package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.root.HardwareProfiler
import com.jeissonalberto.thermaguard.ui.theme.LocalTgColors

@Composable
fun HardwareScreen() {
    val tg = LocalTgColors.current

    // Cargamos el perfil en un LaunchedEffect para no bloquear el hilo principal
    var profile by remember { mutableStateOf<HardwareProfiler.DeviceProfile?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            profile = HardwareProfiler.getProfile()
        }
        loading = false
    }

    val scroll = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(tg.bg)) {

        // Glow decorativo
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-40).dp)
                .background(tg.blue.copy(alpha = 0.18f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Título ────────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    tint = tg.blue,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Hardware del Dispositivo",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = tg.textPri
                )
            }
            Text(
                "Perfil detectado en tiempo de ejecución",
                fontSize = 12.sp,
                color = tg.textSec
            )

            HorizontalDivider(color = tg.surface.copy(alpha = 0.6f))

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = tg.blue, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Detectando hardware...", color = tg.textSec, fontSize = 13.sp)
                    }
                }
            } else {
                val p = profile
                if (p == null) {
                    HwCard(tg) {
                        Text("No se pudo leer el perfil de hardware.", color = tg.red)
                    }
                } else {

                    // ── CHIPSET ───────────────────────────────────────────────
                    HwSectionHeader("Chipset", Icons.Default.DeveloperBoard, tg)
                    HwCard(tg) {
                        HwRow("Identificador", p.chipset, tg)
                        HwRow("Fabricante", when {
                            p.isQualcomm -> "Qualcomm (Snapdragon)"
                            p.isExynos   -> "Samsung (Exynos)"
                            p.isMediatek -> "MediaTek"
                            p.isTensor   -> "Google (Tensor)"
                            else         -> "Desconocido"
                        }, tg)
                    }

                    // ── CPU ───────────────────────────────────────────────────
                    HwSectionHeader("Procesador (CPU)", Icons.Default.Speed, tg)
                    HwCard(tg) {
                        HwRow("Cores totales", "${p.cpuCores}", tg)
                        HwRow("Clusters", "${p.cpuClusters.size}", tg)
                        HwRow("Governors disponibles", p.governors.joinToString(", "), tg)
                    }

                    // Clusters individuales
                    p.cpuClusters.forEachIndexed { idx, cluster ->
                        val clusterLabel = when {
                            cluster.maxFreqKhz == p.cpuClusters.maxOfOrNull { it.maxFreqKhz } -> "Prime / Big"
                            cluster.maxFreqKhz == p.cpuClusters.minOfOrNull { it.maxFreqKhz } -> "Little / Eficiencia"
                            else -> "Middle"
                        }
                        HwCard(tg, accent = when (clusterLabel) {
                            "Prime / Big" -> tg.blue
                            "Little / Eficiencia" -> tg.green
                            else -> tg.amber
                        }) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Cluster $idx — $clusterLabel",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tg.textPri
                                )
                                Text(
                                    "${cluster.maxFreqKhz / 1000} MHz",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tg.blue
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            HwRow("Cores", cluster.cores.joinToString(", ") { "cpu$it" }, tg)
                            HwRow("Rango", "${cluster.minFreqKhz/1000} – ${cluster.maxFreqKhz/1000} MHz", tg)
                            HwRow("Governor actual", cluster.governor, tg)

                            // Barra visual de frecuencia relativa al cluster más potente
                            val peak = p.cpuClusters.maxOfOrNull { it.maxFreqKhz } ?: 1L
                            val pct  = (cluster.maxFreqKhz.toFloat() / peak)
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(tg.surface)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(pct)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            when (clusterLabel) {
                                                "Prime / Big" -> tg.blue
                                                "Little / Eficiencia" -> tg.green
                                                else -> tg.amber
                                            }
                                        )
                                )
                            }
                        }
                    }

                    // ── GPU ───────────────────────────────────────────────────
                    HwSectionHeader("Procesador Gráfico (GPU)", Icons.Default.Videocam, tg)
                    HwCard(tg) {
                        HwRow("Backend", p.gpuBackend.name, tg)
                        HwRow("Max freq path",  p.gpuPaths.maxFreqPath  ?: "No disponible", tg)
                        HwRow("Governor path",  p.gpuPaths.governorPath ?: "No disponible", tg)
                        HwRow("Freq actual path", p.gpuPaths.curFreqPath ?: "No disponible", tg)
                        HwRow("Busy path",      p.gpuPaths.busyPath     ?: "No disponible", tg)
                    }

                    // ── ZONAS TÉRMICAS ─────────────────────────────────────────
                    HwSectionHeader("Zonas Térmicas (${p.thermalZones.size})", Icons.Default.Thermostat, tg)
                    if (p.thermalZones.isEmpty()) {
                        HwCard(tg) {
                            Text("No se encontraron zonas térmicas en /sys/class/thermal.", color = tg.textSec, fontSize = 13.sp)
                        }
                    } else {
                        // Agrupar por tipo relevante — mostrar máximo 12 para no saturar
                        val relevant = p.thermalZones
                            .sortedByDescending { it.tempC }
                            .take(12)
                        HwCard(tg) {
                            relevant.forEachIndexed { idx, zone ->
                                if (idx > 0) HorizontalDivider(
                                    color = tg.surface.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            zone.type,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tg.textPri
                                        )
                                        Text(
                                            "zone${zone.index}",
                                            fontSize = 10.sp,
                                            color = tg.textSec
                                        )
                                    }
                                    val tempColor = when {
                                        zone.tempC >= 70f -> tg.red
                                        zone.tempC >= 50f -> tg.amber
                                        zone.tempC >= 35f -> Color(0xFFFFD740)
                                        else              -> tg.green
                                    }
                                    Text(
                                        "${zone.tempC.toInt()}°C",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tempColor
                                    )
                                }
                            }
                            if (p.thermalZones.size > 12) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "... y ${p.thermalZones.size - 12} zonas más",
                                    fontSize = 11.sp,
                                    color = tg.textSec,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // ── COMPATIBILIDAD ─────────────────────────────────────────
                    HwSectionHeader("Compatibilidad Root", Icons.Default.Shield, tg)
                    HwCard(tg) {
                        val compat = com.jeissonalberto.thermaguard.domain.CpuGpuGovernor.getCompatibilityReport()
                        compat.forEach { line ->
                            Text(line, fontSize = 13.sp, color = tg.textPri,
                                modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Botón redetectar ───────────────────────────────────────
                    Button(
                        onClick = {
                            HardwareProfiler.resetCache()
                            profile = null
                            loading = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = tg.blue)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Redetectar hardware")
                    }
                }
            }
        }
    }
}

// ── Composables auxiliares ────────────────────────────────────────────────────

@Composable
private fun HwSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tg: com.jeissonalberto.thermaguard.ui.theme.TgColors
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(icon, contentDescription = null, tint = tg.blue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tg.blue)
    }
}

@Composable
private fun HwCard(
    tg: com.jeissonalberto.thermaguard.ui.theme.TgColors,
    accent: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = tg.surface),
        border = if (accent != Color.Transparent)
            BorderStroke(1.dp, accent.copy(alpha = 0.35f)) else null
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun HwRow(
    label: String,
    value: String,
    tg: com.jeissonalberto.thermaguard.ui.theme.TgColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = tg.textSec, modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = 12.sp,
            color = tg.textPri,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.5f)
        )
    }
}
