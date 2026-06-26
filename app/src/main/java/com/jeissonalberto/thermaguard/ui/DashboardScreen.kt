package com.jeissonalberto.thermaguard.ui
import androidx.compose.runtime.*
import androidx.compose.material3.*
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import androidx.compose.foundation.layout.Column

@Composable
fun DashboardScreen(viewModel: ThermalViewModel) {
    val temp by viewModel.batteryTemp.collectAsState()
    Column {
        Text(text = "ThermaGuard v4.2.14 Evolution")
        Text(text = "Temperatura: ${temp}C")
        Text(text = "Estado: Silicon Physics Engine Activo")
    }
}
