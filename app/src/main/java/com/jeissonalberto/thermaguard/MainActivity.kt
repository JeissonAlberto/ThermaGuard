package com.jeissonalberto.thermaguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.jeissonalberto.thermaguard.ui.DashboardScreen
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val viewModel: ThermalViewModel = viewModel()
                    DashboardScreen(viewModel)
                }
            }
        }
    }
}
