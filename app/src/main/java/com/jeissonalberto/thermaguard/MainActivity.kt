package com.jeissonalberto.thermaguard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.service.ThermalMonitorService
import com.jeissonalberto.thermaguard.ui.*
import com.jeissonalberto.thermaguard.ui.theme.ThermaGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThermaGuardTheme {
                ThermaGuardApp(onStartService = {
                    try { startForegroundService(Intent(this, ThermalMonitorService::class.java)) }
                    catch (e: Exception) { }
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermaGuardApp(onStartService: () -> Unit) {
    val viewModel: ThermalViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var permissionsGranted by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    if (!permissionsGranted) {
        PermissionsScreen(onAllGranted = {
            permissionsGranted = true
            onStartService()
            viewModel.startMonitor()
        })
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, null) }, label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Science, null) }, label = { Text("Diagnostico") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2, onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("Stats") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3, onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Tune, null) }, label = { Text("Optimizar") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4, onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Notifications, null) }, label = { Text("Alertas") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> DashboardScreen(uiState = uiState,
                        onToggleMonitor = viewModel::toggleMonitorService,
                        onToggleAutoMode = viewModel::toggleAutoMode)
                    1 -> DiagnosisScreen(uiState = uiState)
                    2 -> StatsScreen(uiState = uiState, onResetLearning = viewModel::resetLearning)
                    3 -> OptimizeScreen(
                        uiState = uiState,
                        onCoolingMode = viewModel::activateCoolingMode,
                        onKillApps = viewModel::killApps,
                        onFreeRam = viewModel::freeRam
                    )
                    4 -> AlertsScreen(uiState = uiState,
                        onThresholdChange = viewModel::setAlertThreshold,
                        onToggleAutoMode = viewModel::toggleAutoMode)
                }
            }
        }
    }
}
