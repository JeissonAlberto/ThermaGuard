package com.jeissonalberto.thermaguard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jeissonalberto.thermaguard.data.ThermalSnapshot
import com.jeissonalberto.thermaguard.data.toThermalLevel
import com.jeissonalberto.thermaguard.domain.ThermalUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    uiState: ThermalUiState,
    onExportCsv: () -> String,
) {
    val history = uiState.history
    val sdf     = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }
    val context = LocalContext.current

    var filterHot      by rememberSaveable { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }

    var pageSize by rememberSaveable { mutableIntStateOf(50) }
    val allDisplayed = if (filterHot) history.filter { it.batteryTemp >= 40f } else history
    val displayed = allDisplayed.take(pageSize)
    val hasMore   = allDisplayed.size > pageSize

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TG.bg)
            .padding(horizontal = 16.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text("Historial", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TG.textPri)
                Text("${history.size} lecturas guardadas", fontSize = 12.sp, color = TG.textSec)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterHot,
                    onClick  = { filterHot = !filterHot },
                    label    = { Text("≥ 40°C", fontSize = 11.sp) },
                    leadingIcon = { Text("🔥", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TG.red.copy(alpha = 0.2f),
                        selectedLabelColor     = TG.red
                    )
                )
                Box {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Default.Share, "Exportar", tint = TG.cyan)
                    }
                    DropdownMenu(
                        expanded         = showExportMenu,
                        onDismissRequest = { showExportMenu = false },
                        modifier         = Modifier.background(Color(0xFF0D1520))
                    ) {
                        DropdownMenuItem(
                            text        = { Text("Exportar CSV", color = TG.textPri) },
                            leadingIcon = { Icon(Icons.Default.TableChart, null, tint = TG.cyan, modifier = Modifier.size(18.dp)) },
                            onClick     = {
                                showExportMenu = false
                                try {
                                    val path = onExportCsv()
                                    shareFile(context, File(path), "text/csv")
                                    exportFeedback = "CSV exportado ✓"
                                } catch (e: Exception) {
                                    exportFeedback = "Error exportando: ${e.message}"
                                }
                            }
                        )
                        DropdownMenuItem(
                            text        = { Text("Exportar JSON", color = TG.textPri) },
                            leadingIcon = { Icon(Icons.Default.Code, null, tint = TG.green, modifier = Modifier.size(18.dp)) },
                            onClick     = {
                                showExportMenu = false
                                try {
                                    val path = exportJson(context, history)
                                    shareFile(context, File(path), "application/json")
                                    exportFeedback = "JSON exportado ✓"
                                } catch (e: Exception) {
                                    exportFeedback = "Error exportando: ${e.message}"
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Feedback toast inline ──────────────────────────────────────────
        exportFeedback?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2500)
                exportFeedback = null
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TG.green.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) {
                Text(msg, color = TG.green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Resumen estadístico ────────────────────────────────────────────
        if (history.isNotEmpty()) {
            val maxT  = history.maxOf { it.batteryTemp }
            val minT  = history.minOf { it.batteryTemp }
            val avgT  = history.map { it.batteryTemp }.average().toFloat()
            val hotN  = history.count { it.batteryTemp >= 40f }
            val maxColor = when {
                maxT >= 50f -> TG.red
                maxT >= 43f -> Color(0xFFFF6D00)
                maxT >= 38f -> TG.amber
                else        -> TG.green
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(TG.glass)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HistStatItem("MAX",  "${maxT.toInt()}°C", maxColor)
                HistStatItem("MIN",  "${minT.toInt()}°C", TG.green)
                HistStatItem("PROM", "${avgT.toInt()}°C", TG.cyan)
                HistStatItem("🔥",   "$hotN",             TG.amber)
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Lista de registros ─────────────────────────────────────────────
        if (displayed.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (filterHot) "Sin eventos calientes registrados" else "Sin datos — activa el monitor",
                        color = TG.textSec, fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(displayed, key = { it.timestamp }) { snap ->
                    HistoryRowCard(snap, sdf)
                }
            }
        }
    }
}

@Composable
private fun HistStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = TG.textDim, letterSpacing = 0.5.sp)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
private fun HistoryRowCard(snap: ThermalSnapshot, sdf: SimpleDateFormat) {
    val level  = snap.batteryTemp.toThermalLevel()
    val accent = TG.accentFor(level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TG.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${level.emoji} ${snap.batteryTemp}°C  ·  CPU ${snap.cpuUsage.toInt()}%",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri
            )
            Text(
                sdf.format(Date(snap.timestamp)),
                fontSize = 11.sp, color = TG.textSec
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (snap.isCharging) "⚡ Cargando" else "🔋 ${snap.batteryLevel}%",
                fontSize = 11.sp, color = TG.textSec
            )
            if (snap.topApp.isNotEmpty()) {
                Text(snap.topApp.take(18), fontSize = 10.sp, color = TG.textDim)
            }
        }
    }
}

// ── Helpers de exportación ─────────────────────────────────────────────────

private fun exportJson(context: Context, history: List<ThermalSnapshot>): String {
    val sb  = StringBuilder("[")
    val dtf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    history.forEachIndexed { i, snap ->
        val dt = dtf.format(Date(snap.timestamp))
        sb.append(
            """{"ts":"$dt","battTemp":${snap.batteryTemp},"cpu":${snap.cpuUsage.toInt()},"bat":${snap.batteryLevel},"charging":${snap.isCharging},"app":"${snap.topApp}"}"""
        )
        if (i < history.size - 1) sb.append(",")
    }
    sb.append("]")
    val file = File(
        context.getExternalFilesDir(null),
        "thermaguard_${System.currentTimeMillis()}.json"
    )
    file.writeText(sb.toString())
    return file.absolutePath
}

private fun shareFile(context: Context, file: File, mimeType: String) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir historial"))
    } catch (_: Exception) {}
}

// Mantener compatibilidad con llamadas legacy desde MainActivity que usan HistoryScreen(history = ...)
@Composable
fun HistoryScreen(history: List<ThermalSnapshot>) {
    HistoryScreen(
        uiState      = com.jeissonalberto.thermaguard.domain.ThermalUiState(history = history),
        onExportCsv  = { "" }
    )
}
