package com.jeissonalberto.thermaguard
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.activity.viewModels
import com.jeissonalberto.thermaguard.domain.ThermalViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ThermalViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("ThermaGuard v4.0 Evolution - IA Predictiva Activa")
        }
    }
}
