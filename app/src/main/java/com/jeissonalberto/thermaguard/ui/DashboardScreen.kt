package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.domain.AndroidSettingsTarget
import com.jeissonalberto.thermaguard.domain.MonitoringMode
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.domain.isSystemThermalRisk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: ThermalViewModel,
    onOpenSettings: (AndroidSettingsTarget) -> Unit
) {
    val temp by viewModel.batteryTemp.collectAsState()
    val available by viewModel.sensorAvailable.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val isCharging by viewModel.isCharging.collectAsState()
    val batteryVoltageMv by viewModel.batteryVoltageMv.collectAsState()
    val batteryCurrentMicroamps by viewModel.batteryCurrentMicroamps.collectAsState()
    val status by viewModel.engineStatus.collectAsState()
    val systemThermalStatus by viewModel.systemThermalStatus.collectAsState()
    val threshold by viewModel.alertThreshold.collectAsState()
    val lastUpdated by viewModel.lastUpdated.collectAsState()
    val history by viewModel.history.collectAsState()
    val historyStorageError by viewModel.historyStorageError.collectAsState()
    val monitoringMode by viewModel.monitoringMode.collectAsState()
    val systemThermalRisk = isSystemThermalRisk(systemThermalStatus)

    val temperatureLabel = temp?.let {
        String.format(Locale.getDefault(), "%.1f°C", it)
    } ?: "—"
    val updatedLabel = lastUpdated?.let {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
    } ?: "sin lectura"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010204))
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize()
        ) {
            Text(
                "THERMAGUARD v4.5.1",
                color = Color(0xFF00F2FF),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                "MONITOR TÉRMICO DEL DISPOSITIVO",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MODO DE MONITOREO: ${monitoringMode.label}", color = Color(0xFF00F2FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Cada ${monitoringMode.intervalMinutes} min${if (monitoringMode == MonitoringMode.PREVENTIVE) " • solo mientras carga" else ""}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        monitoringMode.description,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MonitoringMode.values().forEach { mode ->
                            Button(
                                onClick = { viewModel.setMonitoringMode(mode) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(mode.label, fontSize = 9.sp)
                            }
                        }
                    }
                    Text(
                        "Con batería ≤15% y sin carga se omite la persistencia no esencial; las alertas térmicas siguen evaluándose.",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CONFIGURACIÓN ANDROID", color = Color(0xFF00F2FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Accesos directos para revisar opciones del sistema. ThermaGuard no cambia ajustes protegidos.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AndroidSettingsTarget.values().forEach { target ->
                            Button(
                                onClick = { onOpenSettings(target) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(target.label, fontSize = 8.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    temperatureLabel,
                    fontSize = 72.sp,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraLight
                )
                Text(
                    "ESTADO: $status",
                    color = if (available) Color(0xFF00F2FF) else Color(0xFFFFB74D),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
                Text(
                    if (available) "LECTURA REAL • BATERÍA • $updatedLabel"
                    else "ESTE DISPOSITIVO NO EXPONE TEMPERATURA DE BATERÍA",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp
                )
                Button(
                    onClick = viewModel::refreshReading,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("ACTUALIZAR LECTURA")
                }
            }

            if (status == "ALERT" || status == "CRITICAL") {
                val isCritical = status == "CRITICAL"
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = (if (isCritical) Color(0xFF5D1717) else Color(0xFF5A3A12))
                            .copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            if (isCritical) "ALERTA CRÍTICA" else "ALERTA TÉRMICA",
                            color = if (isCritical) Color(0xFFFF8A80) else Color(0xFFFFCC80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            temp?.let {
                                if (isCritical) {
                                    String.format(
                                        Locale.getDefault(),
                                        "Batería a %.1f°C • nivel crítico",
                                        it
                                    )
                                } else {
                                    String.format(
                                        Locale.getDefault(),
                                        "Batería a %.1f°C (umbral %.0f°C)",
                                        it,
                                        threshold
                                    )
                                }
                            } ?: "Lectura no disponible",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            "Reduce la carga del dispositivo y comprueba su ventilación.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("FUENTE", color = Color.Gray, fontSize = 12.sp)
                        Text(
                            if (available) "Android BatteryManager" else "No disponible",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            batteryLevel?.let { level ->
                                val chargingLabel = when (isCharging) {
                                    true -> "cargando"
                                    false -> "sin carga"
                                    null -> "estado de carga no disponible"
                                }
                                "Batería: $level% • $chargingLabel"
                            } ?: "Nivel de batería no disponible",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                        Text(
                            buildList {
                                batteryVoltageMv?.let { add("${it}mV") }
                                batteryCurrentMicroamps?.let { add("${it / 1000f}mA") }
                            }.joinToString(" • ").ifEmpty { "Voltaje/corriente no disponibles" },
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ALERTA", color = Color.Gray, fontSize = 12.sp)
                        Text(
                            "≥ ${threshold.toInt()}°C",
                            color = Color(0xFFFFB74D),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (systemThermalRisk) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF5D1717).copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "ESTADO TÉRMICO DEL SISTEMA",
                            color = Color(0xFFFF8A80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Android reporta ${systemThermalStatus ?: "un estado elevado"}. Reduce la carga del dispositivo.",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            "Es una señal agregada del sistema; no identifica por sí sola CPU, GPU o batería.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("HISTORIAL LOCAL", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        when {
                            historyStorageError -> "No disponible en este dispositivo"
                            history.isEmpty() -> "Sin lecturas persistidas"
                            else -> "${history.size} lecturas recientes • retención de 24 h"
                        },
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        "Se guarda localmente una lectura real por minuto; sin envío externo ni datos inventados.",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                    history.firstOrNull()?.let { latest ->
                        val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(latest.timestamp))
                        Text(
                            String.format(
                                Locale.getDefault(),
                                "Última lectura: %.1f°C • %s",
                                latest.batteryTemp,
                                timeLabel
                            ),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                        val metadata = buildList {
                            latest.batteryLevel?.let { add("$it%") }
                            latest.batteryVoltageMv?.let { add("${it}mV") }
                            latest.batteryCurrentMicroamps?.let { add("${it / 1000f}mA") }
                        }.joinToString(" • ")
                        if (metadata.isNotEmpty()) {
                            Text(
                                "Telemetría local: $metadata",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp
                            )
                        }
                    }
                    history.drop(1).lastOrNull()?.let { oldest ->
                        val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(oldest.timestamp))
                        Text(
                            String.format(
                                Locale.getDefault(),
                                "Más antigua disponible: %.1f°C • %s",
                                oldest.batteryTemp,
                                timeLabel
                            ),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
