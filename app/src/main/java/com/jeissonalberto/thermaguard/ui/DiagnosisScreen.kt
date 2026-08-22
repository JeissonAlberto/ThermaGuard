package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jeissonalberto.thermaguard.domain.DiagnosticUnavailableReason
import com.jeissonalberto.thermaguard.domain.DiagnosticValue
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.domain.isSystemThermalRisk
import com.jeissonalberto.thermaguard.domain.unavailableLabel
import com.jeissonalberto.thermaguard.domain.valueOrNull
import com.jeissonalberto.thermaguard.ui.theme.TGCritical
import com.jeissonalberto.thermaguard.ui.theme.TGPrimary
import com.jeissonalberto.thermaguard.ui.theme.TGTextMuted
import java.util.Locale

@Composable
fun DiagnosisScreen(viewModel: ThermalViewModel) {
    DisposableEffect(viewModel) {
        viewModel.setDiagnosticsVisible(true)
        onDispose { viewModel.setDiagnosticsVisible(false) }
    }
    val diagnostic by viewModel.diagnosticContract.collectAsState()
    val hardwareThermalZones by viewModel.hardwareThermalZones.collectAsState()
    val history by viewModel.history.collectAsState()
    val temperature = diagnostic.batteryTemperature.valueOrNull()
    val sensorAvailable = diagnostic.batteryTemperature is DiagnosticValue.Available
    val status = diagnostic.appStatus
    val systemThermalStatus = diagnostic.systemThermalStatus.valueOrNull()
    val batteryLevel = diagnostic.batteryLevelPercent.valueOrNull()
    val isCharging = diagnostic.charging.valueOrNull()
    val batteryVoltageMv = diagnostic.batteryVoltageMv.valueOrNull()
    val batteryCurrentMicroamps = diagnostic.batteryCurrentMicroamps.valueOrNull()
    val lastUpdated = diagnostic.observedAtMs
    val historyStorageError = diagnostic.historyCount is DiagnosticValue.Unavailable &&
        (diagnostic.historyCount as DiagnosticValue.Unavailable).reason == DiagnosticUnavailableReason.LOCAL_STORAGE_UNAVAILABLE
    val retentionHours by viewModel.retentionHours.collectAsState()
    val costMeasurementEnabled by viewModel.costMeasurementEnabled.collectAsState()
    val monitoringCost by viewModel.monitoringCost.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    val systemThermalRisk = isSystemThermalRisk(systemThermalStatus)
    val diagnosis = when {
        systemThermalRisk -> "Android reporta un estado térmico del sistema que requiere reducir la carga."
        !sensorAvailable -> "No hay diagnóstico térmico: Android no entregó una temperatura de batería válida."
        status == "CRITICAL" -> "Riesgo térmico alto según la lectura actual de batería."
        status == "ALERT" -> "La lectura supera el umbral de alerta; se requiere vigilancia."
        historyStorageError -> "La lectura actual es válida, pero no se pudo verificar el historial local."
        else -> "Lectura térmica disponible y dentro del umbral de alerta."
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader("Verificación", "Diagnóstico", "Señales que ThermaGuard puede comprobar en este dispositivo, sin afirmar datos que Android no expone.")
        TGCard(containerColor = if (systemThermalRisk || status == "CRITICAL") com.jeissonalberto.thermaguard.ui.theme.TGCriticalContainer else MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                SectionLabel("Resultado")
                Text(diagnosis, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp), color = if (systemThermalRisk || status == "CRITICAL") TGCritical else MaterialTheme.colorScheme.onSurface)
            }
        }
        SectionLabel("Señales comprobadas")
        TGCard {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                KeyValueRow("Sensor de temperatura", diagnostic.batteryTemperature.unavailableLabel())
                KeyValueRow("Estado térmico de Android", systemThermalStatus ?: "No disponible: ${diagnostic.systemThermalStatus.unavailableLabel()}")
                KeyValueRow("Temperatura actual", temperature?.let { String.format(Locale.getDefault(), "%.1f°C", it) } ?: "No disponible: ${diagnostic.batteryTemperature.unavailableLabel()}")
                if (hardwareThermalZones.isEmpty()) KeyValueRow("Zonas térmicas del kernel", "No disponible: ${diagnostic.kernelThermalZoneCount.unavailableLabel()}")
                else {
                    KeyValueRow("Zonas térmicas del kernel", "${hardwareThermalZones.size} detectadas")
                    hardwareThermalZones.take(8).forEach { zone -> KeyValueRow("Zona ${zone.type}", "%.1f°C".format(zone.tempC)) }
                }
                KeyValueRow("Nivel de batería", batteryLevel?.let { "$it%" } ?: "No disponible: ${diagnostic.batteryLevelPercent.unavailableLabel()}")
                KeyValueRow("Estado de carga", when (isCharging) { true -> "Cargando"; false -> "Sin carga"; null -> "No disponible: ${diagnostic.charging.unavailableLabel()}" })
                KeyValueRow("Voltaje de batería", batteryVoltageMv?.let { "$it mV" } ?: "No disponible: ${diagnostic.batteryVoltageMv.unavailableLabel()}")
                KeyValueRow("Corriente de batería", batteryCurrentMicroamps?.let { "${it / 1000f} mA" } ?: "No disponible: ${diagnostic.batteryCurrentMicroamps.unavailableLabel()}")
                KeyValueRow("Última actualización", if (lastUpdated != null) "Recibida" else "Pendiente")
                KeyValueRow("Historial local", when { historyStorageError -> "No disponible: ${diagnostic.historyCount.unavailableLabel()}"; else -> "${history.size} lecturas" })
            }
        }
        Text("Las señales se conservan únicamente en este dispositivo durante $retentionHours horas.", style = MaterialTheme.typography.bodySmall, color = TGTextMuted)

        TGCard {
            Column(modifier = Modifier.padding(18.dp)) {
                SectionLabel("Costo local de la app · opcional")
                Text(
                    if (!costMeasurementEnabled) "Desactivado. Si lo autorizas, solo se guardan conteo y duración de cada lectura en este dispositivo."
                    else if (monitoringCost.sampleCount == 0) "Medición autorizada; todavía no hay muestras."
                    else "${monitoringCost.sampleCount} muestras • promedio ${monitoringCost.averageElapsedMs} ms • máximo ${monitoringCost.maxElapsedMs} ms",
                    style = MaterialTheme.typography.bodyMedium, color = TGTextMuted, modifier = Modifier.padding(top = 8.dp)
                )
                Text("Si el promedio supera 100 ms en al menos 5 muestras, la frecuencia visible puede duplicarse. No mide ni modifica CPU/GPU.", style = MaterialTheme.typography.bodySmall, color = TGTextMuted, modifier = Modifier.padding(top = 7.dp))
                Button(onClick = { viewModel.setCostMeasurementEnabled(!costMeasurementEnabled) }, modifier = Modifier.padding(top = 12.dp)) { Text(if (costMeasurementEnabled) "Desactivar y borrar medición" else "Autorizar medición local") }
            }
        }
        RowActions(onRetention = { showRetentionDialog = true }, onDelete = { showDeleteDialog = true }, deleteEnabled = !historyStorageError && (history.isNotEmpty() || monitoringCost.sampleCount > 0))
        Text("El estado térmico de Android es una señal agregada y no identifica un componente concreto ni sustituye sus protecciones.", style = MaterialTheme.typography.bodySmall, color = TGTextMuted)
    }

    if (showRetentionDialog) AlertDialog(
        onDismissRequest = { showRetentionDialog = false },
        title = { Text("Retención local") },
        text = { Column { Text("Elige cuánto tiempo conservar las lecturas en este dispositivo."); listOf(6, 24, 72).forEach { hours -> TextButton(onClick = { viewModel.setRetentionHours(hours); showRetentionDialog = false }) { Text(if (hours == retentionHours) "$hours horas (actual)" else "$hours horas") } } } },
        confirmButton = { TextButton(onClick = { showRetentionDialog = false }) { Text("Cerrar") } }
    )
    if (showDeleteDialog) AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Borrar historial local") },
        text = { Text("Se eliminarán las lecturas térmicas, batería y el agregado opcional del costo guardados en este dispositivo. Esta acción no se puede deshacer.") },
        confirmButton = { Button(onClick = { viewModel.clearLocalHistory(); showDeleteDialog = false }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun RowActions(onRetention: () -> Unit, onDelete: () -> Unit, deleteEnabled: Boolean) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onRetention, modifier = Modifier.weight(1f)) { Text("Cambiar retención") }
        OutlinedButton(onClick = onDelete, enabled = deleteEnabled, modifier = Modifier.weight(1f)) { Text("Borrar historial") }
    }
}
