package com.jeissonalberto.thermaguard.domain
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.jeissonalberto.thermaguard.data.ThermalLevel

class ThermalViewModel : ViewModel() {
    val batteryTemp = MutableStateFlow(35.0f)
    val alertThreshold = MutableStateFlow(42.0f)
    
    fun updateTemp(newTemp: Float) { batteryTemp.value = newTemp }
}
