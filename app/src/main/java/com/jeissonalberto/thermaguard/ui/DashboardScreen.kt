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
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: ThermalViewModel) {
    val temp by viewModel.batteryTemp.collectAsState()
    val available by viewModel.sensorAvailable.collectAsState()
    val status by viewModel.engineStatus.collectAsState()
    val threshold by viewModel.alertThreshold.collectAsState()
    val lastUpdated by viewModel.lastUpdated.collectAsState()

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

            Spacer(modifier = Modifier.height(60.dp))

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
        }
    }
}
