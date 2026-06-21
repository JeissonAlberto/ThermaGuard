package com.jeissonalberto.thermaguard.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.ThermalUiState
import com.jeissonalberto.thermaguard.ui.TG
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.jeissonalberto.thermaguard.ui.theme.LocalTgColors

@Composable
fun LogsScreen(uiState: ThermalUiState) {
    val tg = LocalTgColors.current
    val allLogs: List<SensorLog> = uiState.sensorLogs.reversed()
    val clipboard = LocalClipboardManager.current
    val context   = LocalContext.current

    var filterTag by remember { mutableStateOf("ALL") }
    var showRaw   by remember { mutableStateOf(false) }
    val tags = listOf("ALL", "THERMAL", "CPU", "BATTERY", "RAM", "SENSOR", "RAW")

    val filtered: List<SensorLog> = if (filterTag == "ALL") allLogs
        else allLogs.filter { log: SensorLog -> log.tag == filterTag }

    val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val fmtFull = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    val exportText: String = buildString {
        appendLine("===================================================")
        appendLine("  ThermaGuard - Sensor Log Export")
        appendLine("  ${fmtFull.format(Date())}")
        appendLine("  Entradas: ${filtered.size}")
        appendLine("===================================================")
        appendLine()
        filtered.forEach { log: SensorLog ->
            val ts = fmt.format(Date(log.timestamp))
            appendLine("[$ts] [${log.tag}]")
            appendLine("  campo   : ${log.field}")
            appendLine("  fuente  : ${log.source}")
            appendLine("  raw     : ${log.rawValue}")
            appendLine("  valor   : ${log.parsedValue} ${log.unit}")
            if (log.isEstimated) appendLine("  ESTIMADO - sensor no disponible")
            appendLine()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(tg.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Sensor Logs", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = tg.textPri)
                    Text("${filtered.size} lecturas reales del hardware", fontSize = 10.sp, color = tg.textDim)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(exportText)) }) {
                        Icon(Icons.Default.ContentCopy, "Copiar", tint = TG.teal,
                            modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, exportText)
                            putExtra(Intent.EXTRA_SUBJECT, "ThermaGuard_SensorLog.txt")
                        }
                        context.startActivity(Intent.createChooser(share, "Exportar log"))
                    }) {
                        Icon(Icons.Default.Share, "Exportar", tint = TG.amber,
                            modifier = Modifier.size(22.dp))
                    }
                }
            }

            // Filtros
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tags) { tag: String ->
                    val sel = tag == filterTag
                    val tagCol = logTagColor(tag)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (sel) tagCol.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                            .border(1.dp,
                                if (sel) tagCol.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(20.dp))
                            .clickable { filterTag = tag }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(tag, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = if (sel) tagCol else tg.textDim)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Toggle raw
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = showRaw,
                    onCheckedChange = { v: Boolean -> showRaw = v },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TG.teal,
                        checkedTrackColor = TG.teal.copy(alpha = 0.3f)
                    )
                )
                Text("Mostrar zonas RAW del kernel", fontSize = 10.sp, color = tg.textSec)
            }

            Spacer(Modifier.height(6.dp))

            // Lista
            val display: List<SensorLog> = if (showRaw) filtered
                else filtered.filter { log: SensorLog -> log.tag != "RAW" }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("📋", fontSize = 36.sp)
                        Text("Sin registros", fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, color = tg.textPri)
                        Text(
                            if (filterTag == "ALL") "Los logs aparecerán cuando el motor esté activo"
                            else "Sin entradas con el filtro $filterTag",
                            fontSize = 11.sp, color = tg.textDim,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (display.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📡", fontSize = 40.sp)
                                Text("Sin lecturas aun", color = tg.textDim, fontSize = 13.sp)
                                Text("El motor leerá en el próximo ciclo",
                                    color = tg.textDim, fontSize = 10.sp)
                            }
                        }
                    }
                }
                items(display) { log: SensorLog ->
                    LogEntryCard(log = log, fmt = fmt)
                }
            }
            } // end else
        }
    }
}

@Composable
fun LogEntryCard(log: SensorLog, fmt: SimpleDateFormat) {
    val color = logTagColor(log.tag)
    val ts = fmt.format(Date(log.timestamp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (log.isEstimated) 0.03f else 0.05f))
            .border(1.dp,
                if (log.isEstimated) TG.amber.copy(alpha = 0.4f) else color.copy(alpha = 0.15f),
                RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(log.tag, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        color = color, letterSpacing = 0.5.sp)
                }
                Text(log.field, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tg.textPri)
                if (log.isEstimated) Text("⚠ estimado", fontSize = 8.sp, color = TG.amber)
            }
            Text(ts, fontSize = 9.sp, color = tg.textDim, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "${log.parsedValue} ${log.unit}",
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    color = color, fontFamily = FontFamily.Monospace
                )
                Text(log.source, fontSize = 8.sp, color = tg.textDim,
                    fontFamily = FontFamily.Monospace)
            }
            if (log.rawValue != log.parsedValue) {
                Text("raw: ${log.rawValue}", fontSize = 8.sp, color = tg.textDim,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

fun logTagColor(tag: String): Color = when (tag) {
    "THERMAL"  -> Color(0xFFFF5252)
    "CPU"      -> Color(0xFF448AFF)
    "BATTERY"  -> Color(0xFF69F0AE)
    "RAM"      -> Color(0xFFFFD740)
    "SENSOR"   -> Color(0xFF7C4DFF)
    "RAW"      -> Color(0xFF78909C)
    else       -> Color(0xFF90A4AE)
}
