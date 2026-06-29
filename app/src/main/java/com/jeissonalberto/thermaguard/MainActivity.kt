package com.jeissonalberto.thermaguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jeissonalberto.thermaguard.ui.DashboardScreen
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.ui.theme.ThermaGuardTheme
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThermaGuardTheme {
                val viewModel: ThermalViewModel = viewModel()
                DashboardScreen(viewModel)
            }
        }
    }
}
