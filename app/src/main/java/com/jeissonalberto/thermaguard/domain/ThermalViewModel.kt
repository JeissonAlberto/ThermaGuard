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

    private val sensorRepo = SensorRepository(application)
    private val learningEngine = ThermalLearningEngine(application)
    
    private val _uiState = MutableStateFlow(ThermalUiState())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    private val _rootAvailable = MutableStateFlow(false)
    val rootAvailable: StateFlow<Boolean> = _rootAvailable.asStateFlow()

    // UI accessors
    val latest = _uiState.map { it.latest }.stateIn(viewModelScope, SharingStarted.Eagerly, ThermalSnapshot())
    val isMonitoring = MutableStateFlow(true).asStateFlow()
    val isCoolingDown = MutableStateFlow(false).asStateFlow()
    val profile = _uiState.map { it.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, LearnedProfile())
    val siliconAnalysis = MutableStateFlow(SiliconAnalysis()).asStateFlow()
    val coolingRecs = MutableStateFlow(emptyList<CoolingRecommendation>()).asStateFlow()
    val appTheme = MutableStateFlow("System").asStateFlow()
    val pendingUpdate = MutableStateFlow<AppUpdate?>(null).asStateFlow()
    val telemetryOn = MutableStateFlow(true).asStateFlow()
    val userName = MutableStateFlow("User").asStateFlow()
    val deviceNickname = MutableStateFlow("Device").asStateFlow()

    init {
        viewModelScope.launch { 
            while(true) {
                try {
                    val snap = sensorRepo.readSnapshot()
                    val prof = learningEngine.learn(snap)
                    
                    // IA INTEGRATION - SILENT MODE
                    val fut = SiliconPhysics.predictFuture(snap, SiliconPhysics.detectDevicePhysicsParams(), _uiState.value.history)
                    if (fut.expectedTemp2Min > 41f) {
                        RootEngine.setCpuMaxFreq(RootEngine.CpuLevel.THROTTLE)
                    }

                    _uiState.update { it.copy(latest = snap, profile = prof, history = (listOf(snap) + it.history).take(100)) }
                } catch(e:Exception) {}
                delay(3000L)
            }
        }
    }
    
    fun startMonitor() {}
    fun setUserName(s: String) {}
    fun setDeviceNickname(s: String) {}
    fun toggleTelemetry(b: Boolean) {}
    fun setTheme(s: String) {}
    fun checkForUpdates() {}
    fun rootCpuThrottle() { viewModelScope.launch { RootEngine.setCpuMaxFreq(RootEngine.CpuLevel.THROTTLE) } }
}

data class ThermalUiState(
    val latest: ThermalSnapshot = ThermalSnapshot(),
    val profile: LearnedProfile = LearnedProfile(),
    val history: List<ThermalSnapshot> = emptyList(),
    val isMonitoring: Boolean = true,
    val isCoolingDown: Boolean = false,
    val siliconAnalysis: SiliconAnalysis = SiliconAnalysis(),
    val coolingRecs: List<CoolingRecommendation> = emptyList(),
    val alertThreshold: Float = 42f
)
