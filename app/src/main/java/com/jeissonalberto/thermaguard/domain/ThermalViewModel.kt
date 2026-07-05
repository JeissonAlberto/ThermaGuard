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

    private val _optimizationLevel = MutableStateFlow(0f)
    val optimizationLevel: StateFlow<Float> = _optimizationLevel

    private val _engineStatus = MutableStateFlow("ACTIVE")
    val engineStatus: StateFlow<String> = _engineStatus

    init {
        viewModelScope.launch {
            while (true) {
                delay(2000)
                val temp = 36.0f + Random.nextFloat() * 2.5f
                _batteryTemp.value = temp
                _optimizationLevel.value = (temp - 35f) / 10f
                _engineStatus.value = if (temp > 37.5f) "LEARNING & OPTIMIZING" else "STABLE"
            }
        }
    }
}
