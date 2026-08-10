package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.domain.ThermalViewModel

@Composable
fun DiagnosisScreen(viewModel: ThermalViewModel) {
    val temperature by viewModel.batteryTemp.collectAsState()
    val sensorAvailable by viewModel.sensorAvailable.collectAsState()
    val status by viewModel.engineStatus.collectAsState()
    val systemThermalStatus by viewModel.systemThermalStatus.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val isCharging by viewModel.isCharging.collectAsState()
    val lastUpdated by viewModel.lastUpdated.collectAsState()
    val history by viewModel.history.collectAsState()
    val historyStorageError by viewModel.historyStorageError.collectAsState()

    val systemThermalRisk = systemThermalStatus in setOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")
    val diagnosis = when {
        systemThermalRisk -> "Android reporta un estado térmico del sistema que requiere reducir la carga."
        !sensorAvailable -> "Sin diagnóstico térmico: Android no entregó una temperatura de batería válida."
        status == "CRITICAL" -> "Riesgo térmico alto según la lectura de batería actual."
        status == "ALERT" -> "Se requiere vigilancia: la lectura supera el umbral de alerta."
        historyStorageError -> "La lectura actual es válida, pero no se pudo verificar el historial local."
        else -> "Lectura térmica disponible y dentro del umbral de alerta."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("DIAGNÓSTICO", color = Color(0xFF00F2FF), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Evaluación de las señales que la app puede comprobar en este dispositivo.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("RESULTADO", color = Color.Gray, fontSize = 12.sp)
                Text(diagnosis, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text("SEÑALES COMPROBADAS", color = Color.Gray, fontSize = 12.sp)
        DiagnosticRow("Sensor de temperatura", if (sensorAvailable) "Disponible" else "No disponible")
        DiagnosticRow("Estado térmico del sistema", systemThermalStatus ?: "No disponible (Android < 10)")
        DiagnosticRow("Temperatura actual", temperature?.let { "%.1f°C".format(it) } ?: "Sin lectura")
        DiagnosticRow("Nivel de batería", batteryLevel?.let { "$it%" } ?: "No disponible")
        DiagnosticRow(
            "Estado de carga",
            when (isCharging) {
                true -> "Cargando"
                false -> "Sin carga"
                null -> "No disponible"
            }
        )
        DiagnosticRow("Última actualización", if (lastUpdated != null) "Recibida" else "Pendiente")
        DiagnosticRow(
            "Historial local",
            when {
                historyStorageError -> "No disponible"
                else -> "${history.size} lecturas"
            }
        )

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "El estado térmico del sistema es una señal agregada de Android; no identifica un componente concreto ni sustituye sus protecciones.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Text("$label: $value", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
    Spacer(modifier = Modifier.height(6.dp))
}
