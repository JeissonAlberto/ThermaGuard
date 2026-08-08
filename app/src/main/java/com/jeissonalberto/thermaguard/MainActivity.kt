package com.jeissonalberto.thermaguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.ui.AlertsScreen
import com.jeissonalberto.thermaguard.ui.DashboardScreen
import com.jeissonalberto.thermaguard.ui.DiagnosisScreen
import com.jeissonalberto.thermaguard.ui.theme.ThermaGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThermaGuardTheme {
                val viewModel: ThermalViewModel = viewModel()
                ThermaGuardApp(viewModel)
            }
        }
    }
}

@Composable
private fun ThermaGuardApp(viewModel: ThermalViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = listOf(
        Triple("dashboard", "⌂", "INICIO"),
        Triple("alerts", "!", "ALERTAS"),
        Triple("diagnosis", "✓", "DIAGNÓSTICO")
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { (route, icon, label) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = {
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
                        },
                        icon = { Text(icon) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(contentPadding)
        ) {
            composable("dashboard") { DashboardScreen(viewModel) }
            composable("alerts") { AlertsScreen(viewModel) }
            composable("diagnosis") { DiagnosisScreen(viewModel) }
        }
    }
}
