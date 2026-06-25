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

    // ── PROPIEDADES REQUERIDAS POR MAINACTIVITY ───────────────────────────
    val latest          = _uiState.map { it.latest }.stateIn(viewModelScope, SharingStarted.Eagerly, ThermalSnapshot())
    val isMonitoring    = _uiState.map { it.isMonitoring }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val isCoolingDown   = _uiState.map { it.isCoolingDown }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val profile         = _uiState.map { it.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, LearnedProfile())
    val siliconAnalysis = _uiState.map { it.siliconAnalysis }.stateIn(viewModelScope, SharingStarted.Eagerly, SiliconAnalysis())
    val coolingRecs     = _uiState.map { it.coolingRecs }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    // Temas y Actualizaciones
    val appTheme      = MutableStateFlow("System").asStateFlow()
    val pendingUpdate = MutableStateFlow<AppUpdate?>(null).asStateFlow()
    val telemetryOn   = MutableStateFlow(true).asStateFlow()
    val userName      = MutableStateFlow("User").asStateFlow()
    val deviceNickname = MutableStateFlow("Android").asStateFlow()

    init {
        startLiveReading()
        viewModelScope.launch { 
            try { _rootAvailable.value = RootEngine.isRootAvailable() } catch (_:Exception) {} 
        }
    }

    fun startMonitor() {
        _uiState.update { it.copy(isMonitoring = true) }
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
    
    // Funciones stub para evitar errores de compilación en settings
    fun setUserName(name: String) {}
    fun setDeviceNickname(name: String) {}
    fun toggleTelemetry(on: Boolean) {}
    fun setTheme(theme: String) {}
    fun checkForUpdates() {}
}

data class ThermalUiState(
    val latest: ThermalSnapshot = ThermalSnapshot(),
    val profile: LearnedProfile = LearnedProfile(),
    val history: List<ThermalSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val isMonitoring: Boolean = true,
    val isCoolingDown: Boolean = false,
    val siliconAnalysis: SiliconAnalysis = SiliconAnalysis(),
    val coolingRecs: List<CoolingRecommendation> = emptyList(),
    val alertThreshold: Float = 40f
)
