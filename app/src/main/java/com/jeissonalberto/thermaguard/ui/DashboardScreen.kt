package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeissonalberto.thermaguard.domain.AndroidSettingsTarget
import com.jeissonalberto.thermaguard.domain.MonitoringMode
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.domain.isSystemThermalRisk
import com.jeissonalberto.thermaguard.ui.theme.TGCritical
import com.jeissonalberto.thermaguard.ui.theme.TGCriticalContainer
import com.jeissonalberto.thermaguard.ui.theme.TGPrimary
import com.jeissonalberto.thermaguard.ui.theme.TGTextMuted
import com.jeissonalberto.thermaguard.ui.theme.TGWarning
import com.jeissonalberto.thermaguard.ui.theme.TGWarningContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatPollingInterval(intervalMs: Long): String {
    val minutes = intervalMs / 60_000L
    return if (minutes > 0) "${minutes} min" else "${intervalMs / 1_000L} s"
}

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
    val pollingPolicy by viewModel.foregroundPollingPolicy.collectAsState()
    val retentionHours by viewModel.retentionHours.collectAsState()
    val systemThermalRisk = isSystemThermalRisk(systemThermalStatus)
    val tone = statusTone(status, available)
    val updatedLabel = lastUpdated?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) }
        ?: "sin lectura"
    val chargingLabel = when (isCharging) { true -> "Cargando"; false -> "Sin carga"; null -> "No disponible" }
    val batteryMeta = buildList {
        batteryLevel?.let { add("$it%") }
        add(chargingLabel)
        batteryVoltageMv?.let { add("${it} mV") }
        batteryCurrentMicroamps?.let { add("${it / 1000f} mA") }
    }.joinToString(" • ")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            ScreenHeader(
                eyebrow = "Operación térmica",
                title = "Estado del dispositivo",
                description = "Lecturas verificables de Android, almacenadas localmente.",
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = viewModel::refreshReading,
                modifier = Modifier.semantics { contentDescription = "Actualizar lectura" }
            ) { Icon(Icons.Outlined.Refresh, contentDescription = null) }
        }

        TGCard(containerColor = when (tone) {
            StatusTone.CRITICAL -> TGCriticalContainer
            StatusTone.WARNING -> TGWarningContainer
            else -> androidx.compose.material3.MaterialTheme.colorScheme.surface
        }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusPill(
                        label = when (status) { "NOMINAL" -> "NORMAL"; "WAITING" -> "ESPERANDO"; else -> status },
                        tone = tone
                    )
                    Text("Actualizado $updatedLabel", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TGTextMuted)
                }
                Text(
                    temp?.let { String.format(Locale.getDefault(), "%.1f°C", it) } ?: "—",
                    style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(top = 14.dp),
                    color = when (tone) { StatusTone.CRITICAL -> TGCritical; StatusTone.WARNING -> TGWarning; else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface }
                )
                Text(
                    when {
                        available -> "Temperatura de batería • lectura real"
                        lastUpdated != null -> "Última lectura conservada • el sensor no está disponible ahora"
                        else -> "Este dispositivo no expone temperatura de batería"
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = TGTextMuted
                )
                Button(
                    onClick = viewModel::refreshReading,
                    modifier = Modifier.padding(top = 18.dp),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Actualizar lectura", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        if (status == "ALERT" || status == "CRITICAL") {
            TGCard(containerColor = if (status == "CRITICAL") TGCriticalContainer else TGWarningContainer) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(if (status == "CRITICAL") "Intervención recomendada" else "Vigilancia térmica", style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = if (status == "CRITICAL") TGCritical else TGWarning)
                    Text(
                        temp?.let { if (status == "CRITICAL") "Batería a %.1f°C; reduce la carga y comprueba la ventilación.".format(it) else "Batería a %.1f°C; supera el umbral de %.0f°C.".format(it, threshold) }
                            ?: "Lectura no disponible.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }
        if (systemThermalRisk) {
            TGCard(containerColor = TGCriticalContainer) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Estado térmico del sistema", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TGCritical)
                    Text("Android reporta ${systemThermalStatus ?: "un estado elevado"}. Es una señal agregada; no identifica CPU, GPU ni batería.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Umbral de alerta", "≥ ${threshold.toInt()}°C", "Configurado por la app", Modifier.weight(1f), TGWarning)
            MetricTile("Historial local", when { historyStorageError -> "No disponible"; history.isEmpty() -> "Sin lecturas"; else -> "${history.size} lecturas" }, "Retención: ${retentionHours} h", Modifier.weight(1f))
        }
        TGCard {
            Column(modifier = Modifier.padding(18.dp)) {
                SectionLabel("Últimas lecturas")
                history.firstOrNull()?.let { latest ->
                    Text("Más reciente: %.1f°C • %s".format(latest.batteryTemp, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(latest.timestamp))), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    val metadata = buildList { latest.batteryLevel?.let { add("$it%") }; latest.batteryVoltageMv?.let { add("${it} mV") }; latest.batteryCurrentMicroamps?.let { add("${it / 1000f} mA") } }.joinToString(" • ")
                    if (metadata.isNotEmpty()) Text("Telemetría local: $metadata", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TGTextMuted, modifier = Modifier.padding(top = 4.dp))
                }
                history.drop(1).lastOrNull()?.let { oldest ->
                    Text("Más antigua disponible: %.1f°C • %s".format(oldest.batteryTemp, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(oldest.timestamp))), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TGTextMuted, modifier = Modifier.padding(top = 5.dp))
                }
                if (history.isEmpty()) Text(if (historyStorageError) "El almacenamiento local no está disponible." else "Sin lecturas persistidas.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = TGTextMuted, modifier = Modifier.padding(top = 8.dp))
            }
        }

        TGCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, tint = TGPrimary)
                    Text("Telemetría de batería", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
                }
                Text(batteryMeta.ifEmpty { "Datos de batería no disponibles" }, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = TGTextMuted, modifier = Modifier.padding(top = 10.dp))
                Text(if (available) "Fuente: Android BatteryManager" else "Fuente no disponible", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TGTextMuted, modifier = Modifier.padding(top = 5.dp))
            }
        }

        TGCard {
            Column(modifier = Modifier.padding(18.dp)) {
                SectionLabel("Cadencia de monitoreo")
                Text(monitoringMode.label, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TGPrimary, modifier = Modifier.padding(top = 6.dp))
                Text("${monitoringMode.description} Cada ${monitoringMode.intervalMinutes} min${if (monitoringMode == MonitoringMode.PREVENTIVE) " • solo mientras carga" else ""}.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = TGTextMuted, modifier = Modifier.padding(top = 4.dp))
                Text("Con la pantalla abierta: ${formatPollingInterval(pollingPolicy.intervalMs)}${if (pollingPolicy.lowBatteryLimited) " • ampliada por batería ≤15%" else if (pollingPolicy.costLimited) " • ampliada por costo medido" else ""}.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TGTextMuted, modifier = Modifier.padding(top = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonitoringMode.values().forEach { mode ->
                        FilterChip(selected = monitoringMode == mode, onClick = { viewModel.setMonitoringMode(mode) }, label = { Text(mode.label) })
                    }
                }
                Text("Con batería ≤15% y sin carga se omite la persistencia no esencial; las alertas siguen evaluándose.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TGTextMuted, modifier = Modifier.padding(top = 9.dp))
            }
        }

        TGCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = TGPrimary)
                    Text("Controles del sistema", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
                }
                Text("Accesos directos para revisar ajustes. ThermaGuard no cambia opciones protegidas.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TGTextMuted, modifier = Modifier.padding(top = 5.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AndroidSettingsTarget.values().forEach { target ->
                        OutlinedButton(onClick = { onOpenSettings(target) }) { Text(target.label.replace("FICHA DE LA APP", "FICHA")) }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
