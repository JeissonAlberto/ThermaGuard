package com.jeissonalberto.thermaguard.ui
import androidx.compose.runtime.*
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import androidx.compose.material3.Text

@Composable
fun AlertsScreen(viewModel: ThermalViewModel) {
    val threshold by viewModel.alertThreshold.collectAsState()
    Text(text = "Alert Threshold: ${threshold}C")
}
