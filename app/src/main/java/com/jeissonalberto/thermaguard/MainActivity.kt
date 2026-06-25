package com.jeissonalberto.thermaguard
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.viewModels
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.ui.OptimizeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: ThermalViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Column {
                        Text("ThermaGuard v4.0 Evolution - IA Predictiva Activa")
                        OptimizeScreen(viewModel)
                    }
                }
            }
        }
    }
}
