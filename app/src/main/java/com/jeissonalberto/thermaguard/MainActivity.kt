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
                ThermaGuardApp(
                    onStartService = { startMonitorService() }
                )
            }
        }
    }

    private fun startMonitorService() {
        try {
            val intent = Intent(this, ThermalMonitorService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) { }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermaGuardApp(onStartService: () -> Unit) {
    val viewModel: ThermalViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Control de pantalla: permisos vs app principal
    var permissionsGranted by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    if (!permissionsGranted) {
        PermissionsScreen(
            onAllGranted = {
                permissionsGranted = true
                onStartService()  // Arrancar servicio automático al conceder permisos
                viewModel.startMonitor()
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "Historial") },
                        label = { Text("Historial") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = "Alertas") },
                        label = { Text("Alertas") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        uiState = uiState,
                        onToggleMonitor = viewModel::toggleMonitorService,
                        onToggleAutoMode = viewModel::toggleAutoMode
                    )
                    1 -> HistoryScreen(history = uiState.history)
                    2 -> AlertsScreen(
                        uiState = uiState,
                        onThresholdChange = viewModel::setAlertThreshold,
                        onToggleAutoMode = viewModel::toggleAutoMode
                    )
                }
            }
        }
    }
}
