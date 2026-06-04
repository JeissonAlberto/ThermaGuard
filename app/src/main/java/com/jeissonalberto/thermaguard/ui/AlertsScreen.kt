package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.domain.ThermalUiState

@Composable
fun AlertsScreen(
    uiState: ThermalUiState,
    onThresholdChange: (Float) -> Unit,
    onToggleAutoMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🚨 Alertas y automatización",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        // Umbral de alerta
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "🌡️ Umbral de alerta", fontWeight = FontWeight.Bold)
                Text(
                    text = "Alerta cuando la temperatura supere ${uiState.alertThreshold.toInt()}°C",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = uiState.alertThreshold,
                    onValueChange = onThresholdChange,
                    valueRange = 35f..55f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("35°C", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("55°C", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Modo automático
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.autoMode)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "🤖 Modo automático", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = if (uiState.autoMode)
                            "Activo — optimizando automáticamente cuando detecta calor"
                        else
                            "Inactivo — la app solo monitorea y avisa",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.autoMode,
                    onCheckedChange = { onToggleAutoMode() }
                )
            }
        }

        // Qué hace el modo automático
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "📋 Acciones automáticas por temperatura", fontWeight = FontWeight.Bold)

                AutoActionRow("≥ 37°C", "Baja brillo · Cierra apps background")
                AutoActionRow("≥ 40°C", "Todo anterior + Activa ahorro de batería")
                AutoActionRow("≥ 45°C", "Todo anterior + Desactiva Bluetooth y WiFi")
                AutoActionRow("≥ 50°C", "🚨 Alerta crítica — acción inmediata requerida")
            }
        }

        // Info sobre límites sin root
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "ℹ️ Límites sin root", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    text = "Sin root podemos matar apps en background y ajustar configuraciones del sistema. El control directo del kernel (frecuencias CPU/GPU) requiere root y se habilitará en versión futura.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun AutoActionRow(temp: String, action: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = temp,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.width(55.dp)
        )
        Text(text = action, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
