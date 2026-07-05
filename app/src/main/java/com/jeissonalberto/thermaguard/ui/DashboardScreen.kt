package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.domain.ThermalViewModel

@Composable
fun DashboardScreen(viewModel: ThermalViewModel) {
    val temp by viewModel.batteryTemp.collectAsState()
    val status by viewModel.engineStatus.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.padding(24.dp).align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("THERMAGUARD v4.4.7", color = Color.Cyan, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Text("${"%.1f".format(temp)}°C", fontSize = 72.sp, color = Color.White)
            Text(status, color = Color.Cyan, fontSize = 14.sp)
        }
    }
}
