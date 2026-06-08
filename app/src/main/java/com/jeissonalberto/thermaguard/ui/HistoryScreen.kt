package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.ThermalSnapshot
import com.jeissonalberto.thermaguard.data.toThermalLevel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(history: List<ThermalSnapshot>) {
    val sdf = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "📊 Historial térmico",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        // Resumen de la sesión
    if (history.isNotEmpty()) {
        val maxT = history.maxOf { it.batteryTemp }
        val minT = history.minOf { it.batteryTemp }
        val avgT = history.map { it.batteryTemp }.average().toFloat()
        val maxColor = when {
            maxT >= 50f -> TG.red
            maxT >= 43f -> Color(0xFFFF6D00)
            maxT >= 38f -> TG.amber
            else        -> TG.green
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TG.glass)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Max", fontSize = 10.sp, color = TG.textDim)
                Text("${maxT.toInt()}°C", fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold, color = maxColor)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Min", fontSize = 10.sp, color = TG.textDim)
                Text("${minT.toInt()}°C", fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold, color = TG.green)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Prom", fontSize = 10.sp, color = TG.textDim)
                Text("${avgT.toInt()}°C", fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold, color = TG.textPri)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Lecturas", fontSize = 10.sp, color = TG.textDim)
                Text("${history.size}", fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold, color = TG.textPri)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Sin datos aún. Activa el monitor.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        // Resumen estadístico
        val avgTemp = history.map { it.batteryTemp }.average().toFloat()
        val maxTemp = history.maxOf { it.batteryTemp }
        val hotEvents = history.count { it.batteryTemp >= 40f }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Promedio", "${avgTemp.toInt()}°C")
                StatItem("Máximo", "${maxTemp.toInt()}°C")
                StatItem("Eventos >40°C", "$hotEvents")
            }
        }

        Text(
            text = "Últimas ${history.size} lecturas",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history) { snap ->
                HistoryItem(snap, sdf)
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun HistoryItem(snap: ThermalSnapshot, sdf: SimpleDateFormat) {
    val level = snap.batteryTemp.toThermalLevel()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = sdf.format(Date(snap.timestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${level.emoji} ${snap.batteryTemp}°C — CPU ${snap.cpuUsage.toInt()}%",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (snap.isCharging) "⚡" else "🔋${snap.batteryLevel}%",
                    fontSize = 12.sp
                )
                if (snap.topApp.isNotEmpty()) {
                    Text(
                        text = snap.topApp,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
