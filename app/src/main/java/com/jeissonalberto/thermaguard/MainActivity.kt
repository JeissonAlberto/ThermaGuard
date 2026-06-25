package com.jeissonalberto.thermaguard

import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.ui.OptimizeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: com.jeissonalberto.thermaguard.domain.ThermalViewModel by androidx.activity.viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Text("📊") },
                            label = { Text("Dashboard") },
                            selected = true,
                            onClick = { /* TODO */ }
                        )
                        NavigationBarItem(
                            icon = { Text("🛠️") },
                            label = { Text("Optimizar") },
                            selected = false,
                            onClick = { navController.navigate("optimize") }
                        )
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "optimize",
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable("optimize") {
                        OptimizeScreen(viewModel)
                    }
                }
            }
        }
    }
}
