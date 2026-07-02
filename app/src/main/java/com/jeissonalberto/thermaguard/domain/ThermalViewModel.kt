package com.jeissonalberto.thermaguard.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class ThermalViewModel : ViewModel() {
    private val _batteryTemp = MutableStateFlow(36.5f)
    val batteryTemp: StateFlow<Float> = _batteryTemp

    private val _engineStatus = MutableStateFlow("READY")
    val engineStatus: StateFlow<String> = _engineStatus

    private val _fleetbaseStatus = MutableStateFlow("CONNECTED")
    val fleetbaseStatus: StateFlow<String> = _fleetbaseStatus

    init {
        simulateThermalDynamics()
    }

    private fun simulateThermalDynamics() {
        viewModelScope.launch {
            while (true) {
                delay(2000)
                // Oscilación realista
                _batteryTemp.value = 36.0f + Random.nextFloat() * 1.5f
                _engineStatus.value = if (_batteryTemp.value > 37.2f) "OPTIMIZING" else "ACTIVE"
            }
        }
    }
}
