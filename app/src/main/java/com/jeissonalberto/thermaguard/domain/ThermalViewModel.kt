package com.jeissonalberto.thermaguard.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeissonalberto.thermaguard.data.LearningEngine
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

    private val _engineStatus = MutableStateFlow("LEARNING")
    val engineStatus: StateFlow<String> = _engineStatus

    init {
        runAutoLearningCycle()
    }

    private fun runAutoLearningCycle() {
        viewModelScope.launch {
            while (true) {
                delay(1500)
                val currentTemp = 36.0f + Random.nextFloat() * 2.0f
                _batteryTemp.value = currentTemp
                
                // Inyectar datos al motor de aprendizaje
                LearningEngine.learnFromPattern(currentTemp, 0.5f)
                
                val threshold = LearningEngine.getOptimizedThreshold(40f)
                _optimizationLevel.value = (1.0f - (threshold / 45f)).coerceIn(0f, 1f)
                
                _engineStatus.value = if (currentTemp > threshold) "SELF-OPTIMIZING" else "STABLE"
            }
        }
    }
}
