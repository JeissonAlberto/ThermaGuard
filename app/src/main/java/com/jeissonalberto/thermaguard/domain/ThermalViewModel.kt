package com.jeissonalberto.thermaguard.domain

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.root.RootEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ThermalViewModel(application: Application) : AndroidViewModel(application) {

    private val _prefs = application.getSharedPreferences("thermaguard_prefs", Context.MODE_PRIVATE)
    private val sensorRepo = SensorRepository(application)
    private val learningEngine = ThermalLearningEngine(application)
    
    private val _uiState = MutableStateFlow(ThermalUiState())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    private val _rootAvailable = MutableStateFlow(false)
    val rootAvailable: StateFlow<Boolean> = _rootAvailable.asStateFlow()

    init {
        startLiveReading()
        viewModelScope.launch { 
            try { _rootAvailable.value = RootEngine.isRootAvailable() } catch (_:Exception) {} 
        }
    }

    private fun startLiveReading() {
        viewModelScope.launch {
            delay(1000L)
            while (true) {
                try {
                    val snapshot = sensorRepo.readSnapshot()
                    val profile  = learningEngine.learn(snapshot)
                    
                    // --- EVOLUTION ENGINE IA v4.0 ---
                    val future = SiliconPhysics.predictFuture(snapshot, SiliconPhysics.detectDevicePhysicsParams(), _uiState.value.history)
                    if (future.expectedTemp2Min > 41f) {
                        viewModelScope.launch { RootEngine.setCpuMaxFreq(RootEngine.CpuLevel.THROTTLE) }
                    }

                    _uiState.update { it.copy(
                        latest = snapshot,
                        profile = profile,
                        history = (listOf(snapshot) + it.history).take(200),
                        isLoading = false
                    )}

                } catch (_: Exception) {}
                delay(2000L)
            }
        }
    }
}

data class ThermalUiState(
    val latest: ThermalSnapshot = ThermalSnapshot(),
    val profile: LearnedProfile = LearnedProfile(),
    val history: List<ThermalSnapshot> = emptyList(),
    val isLoading: Boolean = true
)
