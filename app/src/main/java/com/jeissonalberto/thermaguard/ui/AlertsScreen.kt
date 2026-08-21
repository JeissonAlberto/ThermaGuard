package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.domain.isSystemThermalRisk
import com.jeissonalberto.thermaguard.ui.theme.TGPrimary
import com.jeissonalberto.thermaguard.ui.theme.TGTextMuted
import com.jeissonalberto.thermaguard.ui.theme.TGWarning
import java.util.Locale

@Composable
fun AlertsScreen(viewModel: ThermalViewModel) {
    val threshold by viewModel.alertThreshold.collectAsState()
    val temperature by viewModel.batteryTemp.collectAsState()
    val status by viewModel.engineStatus.collectAsState()
    val sensorAvailable by viewModel.sensorAvailable.collectAsState()
    val systemThermalStatus by viewModel.systemThermalStatus.collectAsState()
    val history by viewModel.history.collectAsState()
    val historyStorageError by viewModel.historyStorageError.collectAsState()
    val systemThermalRisk = isSystemThermalRisk(systemThermalStatus)
    val tone = statusTone(status, sensorAvailable)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader("Señales operativas", "Alertas", "Señales basadas en la temperatura real de batería y el estado agregado de Android.")
        TGCard {
            Column(modifier = Modifier.padding(20.dp)) {
                SectionLabel("Estado actual")
                RowStatus(status, tone)
                Text(temperature?.let { String.format(Locale.getDefault(), "Temperatura de batería: %.1f°C", it) } ?: "Temperatura no disponible", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 14.dp))
                Text("Estado térmico de Android: ${systemThermalStatus ?: "no disponible"}", style = MaterialTheme.typography.bodyMedium, color = TGTextMuted, modifier = Modifier.padding(top = 5.dp))
                Text("Umbral de alerta: ≥ ${threshold.toInt()}°C", style = MaterialTheme.typography.bodyMedium, color = TGWarning, modifier = Modifier.padding(top = 8.dp))
            }
        }
        TGCard(containerColor = when (tone) {
            StatusTone.CRITICAL -> com.jeissonalberto.thermaguard.ui.theme.TGCriticalContainer
            StatusTone.WARNING -> com.jeissonalberto.thermaguard.ui.theme.TGWarningContainer
            else -> MaterialTheme.colorScheme.surface
        }) {
            Text(
                when {
                    systemThermalRisk -> "Android reporta un estado térmico ${systemThermalStatus ?: "elevado"}. Reduce la carga y comprueba la ventilación."
                    !sensorAvailable -> "No se puede evaluar la temperatura: este dispositivo no expone una lectura de batería."
                    status == "CRITICAL" -> "Temperatura crítica detectada. Reduce la carga y comprueba la ventilación."
                    status == "ALERT" -> "La lectura supera el umbral. Vigila la tendencia y reduce la carga si continúa subiendo."
                    else -> "No hay una alerta térmica activa según la última lectura disponible."
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(20.dp)
            )
        }
        SectionLabel("Tendencia local")
        TGCard {
            Column(modifier = Modifier.padding(16.dp)) {
                if (history.size >= 2) {
                    ThermalHistoryChart(
                        temperatures = history.asReversed().map { it.batteryTemp }, threshold = threshold,
                        modifier = Modifier.fillMaxWidth().height(170.dp)
                    )
                } else {
                    Text("Aún no hay suficientes lecturas persistidas para mostrar una tendencia.", style = MaterialTheme.typography.bodyMedium, color = TGTextMuted)
                }
            }
        }
        SectionLabel("Evidencia")
        Text(
            when {
                historyStorageError -> "El historial local no está disponible en este dispositivo."
                history.isEmpty() -> "Todavía no hay lecturas persistidas."
                else -> "${history.size} lecturas reales recientes disponibles para contextualizar la alerta."
            }, style = MaterialTheme.typography.bodyMedium, color = TGTextMuted
        )
        Text("El estado térmico de Android es una señal agregada; no representa una lectura de CPU o GPU.", style = MaterialTheme.typography.bodySmall, color = TGTextMuted)
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun RowStatus(status: String, tone: StatusTone) {
    StatusPill(label = when (status) { "NOMINAL" -> "NORMAL"; "WAITING" -> "ESPERANDO"; else -> status }, tone = tone, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun ThermalHistoryChart(temperatures: List<Float>, threshold: Float, modifier: Modifier = Modifier) {
    val chartTemperatures = temperatures.downsampleForChart()
    val minTemperature = chartTemperatures.minOrNull() ?: return
    val maxTemperature = chartTemperatures.maxOrNull() ?: return
    val range = (maxTemperature - minTemperature).coerceAtLeast(1f)
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f).semantics {
            contentDescription = String.format(Locale.getDefault(), "Gráfico de temperatura de batería. Mínima %.1f grados Celsius, máxima %.1f grados Celsius, umbral %.0f grados Celsius.", minTemperature, maxTemperature, threshold)
        }) {
            val left = 8f; val right = size.width - 8f; val top = 10f; val bottom = size.height - 10f
            val step = (right - left) / (chartTemperatures.size - 1).coerceAtLeast(1)
            val points = chartTemperatures.mapIndexed { index, value -> Offset(left + index * step, bottom - ((value - minTemperature) / range) * (bottom - top)) }
            val thresholdY = (bottom - ((threshold - minTemperature) / range) * (bottom - top)).coerceIn(top, bottom)
            drawLine(TGWarning.copy(alpha = .75f), Offset(left, thresholdY), Offset(right, thresholdY), 2f, StrokeCap.Round)
            points.zipWithNext().forEach { (start, end) -> drawLine(TGPrimary, start, end, 4f, StrokeCap.Round) }
        }
        Text(String.format(Locale.getDefault(), "Mín. %.1f°C • Máx. %.1f°C • umbral %.0f°C", minTemperature, maxTemperature, threshold), style = MaterialTheme.typography.bodySmall, color = TGTextMuted, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun List<Float>.downsampleForChart(maxPoints: Int = 180): List<Float> {
    if (size <= maxPoints) return this
    val bucketCount = (maxPoints / 4).coerceAtLeast(1)
    val bucketSize = (size + bucketCount - 1) / bucketCount
    return chunked(bucketSize).flatMap { bucket ->
        val minIndex = bucket.indices.minByOrNull { bucket[it] } ?: 0
        val maxIndex = bucket.indices.maxByOrNull { bucket[it] } ?: 0
        listOf(0, minIndex, maxIndex, bucket.lastIndex).distinct().sorted().map(bucket::get)
    }
}
