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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import java.util.Locale

@Composable
fun AlertsScreen(viewModel: ThermalViewModel) {
    val threshold by viewModel.alertThreshold.collectAsState()
    val temperature by viewModel.batteryTemp.collectAsState()
    val status by viewModel.engineStatus.collectAsState()
    val sensorAvailable by viewModel.sensorAvailable.collectAsState()
    val history by viewModel.history.collectAsState()
    val historyStorageError by viewModel.historyStorageError.collectAsState()

    val statusColor = when (status) {
        "CRITICAL" -> Color(0xFFFF8A80)
        "ALERT" -> Color(0xFFFFCC80)
        "NOMINAL" -> Color(0xFF80CBC4)
        else -> Color(0xFFFFB74D)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("ALERTAS TÉRMICAS", color = Color(0xFF00F2FF), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Basadas únicamente en la temperatura real de batería expuesta por Android.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("ESTADO ACTUAL", color = Color.Gray, fontSize = 12.sp)
                Text(status, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(
                    temperature?.let {
                        String.format(Locale.getDefault(), "Temperatura: %.1f°C", it)
                    } ?: "Temperatura no disponible",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text("Umbral de alerta: ≥ ${threshold.toInt()}°C", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            when {
                !sensorAvailable -> "No se puede activar una alerta térmica: este dispositivo no expone la temperatura de batería."
                status == "CRITICAL" -> "Temperatura crítica detectada. Reduce la carga y comprueba la ventilación del dispositivo."
                status == "ALERT" -> "Temperatura por encima del umbral configurado. Vigila la lectura y reduce la carga si continúa subiendo."
                else -> "No hay una alerta térmica activa según la última lectura disponible."
            },
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text("TENDENCIA DE LAS LECTURAS", color = Color.Gray, fontSize = 12.sp)
        if (history.size >= 2) {
            ThermalHistoryChart(
                temperatures = history.asReversed().map { it.batteryTemp },
                threshold = threshold,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(top = 8.dp)
            )
        } else {
            Text(
                "Se necesitan al menos 2 lecturas persistidas para mostrar una tendencia.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("EVIDENCIA LOCAL", color = Color.Gray, fontSize = 12.sp)
        Text(
            when {
                historyStorageError -> "El almacenamiento local del historial no está disponible."
                history.isEmpty() -> "Todavía no hay lecturas persistidas."
                else -> "${history.size} lecturas reales recientes disponibles para contextualizar la alerta."
            },
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ThermalHistoryChart(
    temperatures: List<Float>,
    threshold: Float,
    modifier: Modifier = Modifier
) {
    val minTemperature = temperatures.minOrNull() ?: return
    val maxTemperature = temperatures.maxOrNull() ?: return
    val range = (maxTemperature - minTemperature).coerceAtLeast(1f)
    val lineColor = Color(0xFF00F2FF)
    val thresholdColor = Color(0xFFFFB74D)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = String.format(
                        Locale.getDefault(),
                        "Gráfico de temperatura de batería. Mínima %.1f grados Celsius, máxima %.1f grados Celsius, umbral %.0f grados Celsius.",
                        minTemperature,
                        maxTemperature,
                        threshold
                    )
                }
        ) {
            val left = 8f
            val right = size.width - 8f
            val top = 8f
            val bottom = size.height - 8f
            val xStep = (right - left) / (temperatures.size - 1).coerceAtLeast(1)
            val points = temperatures.mapIndexed { index, value ->
                val x = left + index * xStep
                val y = bottom - ((value - minTemperature) / range) * (bottom - top)
                Offset(x, y)
            }

            val thresholdY = bottom - ((threshold - minTemperature) / range) * (bottom - top)
            drawLine(
                color = thresholdColor.copy(alpha = 0.65f),
                start = Offset(left, thresholdY.coerceIn(top, bottom)),
                end = Offset(right, thresholdY.coerceIn(top, bottom)),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            points.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = lineColor,
                    start = start,
                    end = end,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
        }
        Text(
            String.format(
                Locale.getDefault(),
                "Mín. %.1f°C • Máx. %.1f°C • línea ámbar: umbral %.0f°C",
                minTemperature,
                maxTemperature,
                threshold
            ),
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp
        )
    }
}
