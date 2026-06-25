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

    // ── PROPIEDADES INDIVIDALES PARA COMPOSED DELEGATES ──────────────────
    val latest          = _uiState.map { it.latest }.stateIn(viewModelScope, SharingStarted.Eagerly, ThermalSnapshot())
    val isMonitoring    = _uiState.map { it.isMonitoring }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val isCoolingDown   = _uiState.map { it.isCoolingDown }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val profile         = _uiState.map { it.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, LearnedProfile())
    val siliconAnalysis = _uiState.map { it.siliconAnalysis }.stateIn(viewModelScope, SharingStarted.Eagerly, SiliconAnalysis())
    val coolingRecs     = _uiState.map { it.coolingRecs }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    val appTheme: StateFlow<String> = MutableStateFlow("System")
    val pendingUpdate: StateFlow<AppUpdate?> = MutableStateFlow(null)
    val telemetryOn: StateFlow<Boolean> = MutableStateFlow(true)
    val userName: StateFlow<String> = MutableStateFlow("User")
    val deviceNickname: StateFlow<String> = MutableStateFlow("Device")
    val rootAvailable  = MutableStateFlow(false)

    init {
        viewModelScope.launch { 
             try { rootAvailable.value = RootEngine.isRootAvailable() } catch(_:Exception){}
        }
        startLiveReading()
    }

    private fun startLiveReading() {
        viewModelScope.launch {
            while (true) {
                try {
                    val snapshot = sensorRepo.readSnapshot()
                    val prof     = learningEngine.learn(snapshot)
                    
                    // IA EVOLUTION
                    val future = SiliconPhysics.predictFuture(snapshot, SiliconPhysics.detectDevicePhysicsParams(), _uiState.value.history)
                    if (future.expectedTemp2Min > 41f) {
                        viewModelScope.launch { RootEngine.setCpuMaxFreq(RootEngine.CpuLevel.THROTTLE) }
                    }

                    _uiState.update { it.copy(
                        latest = snapshot,
                        profile = prof,
                        history = (listOf(snapshot) + it.history).take(100),
                        isLoading = false
                    )}
                } catch (_: Exception) {}
                delay(3000L)
            }
        }
    }

    fun startMonitor() { _uiState.update { it.copy(isMonitoring = true) } }
    fun setUserName(name: String) { userName.value = name }
    fun setDeviceNickname(name: String) { deviceNickname.value = name }
    fun toggleTelemetry(on: Boolean) { telemetryOn.value = on }
    fun setTheme(theme: String) { appTheme.value = theme }
    fun checkForUpdates() {}
    fun rootCpuThrottle() { viewModelScope.launch { RootEngine.setCpuMaxFreq(RootEngine.CpuLevel.THROTTLE) } }
}

data class ThermalUiState(
    val latest: ThermalSnapshot = ThermalSnapshot(),
    val profile: LearnedProfile = LearnedProfile(),
    val history: List<ThermalSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val isMonitoring: Boolean = true,
    val isCoolingDown: Boolean = false,
    val siliconAnalysis: SiliconAnalysis = SiliconAnalysis(),
    val coolingRecs: List<CoolingRecommendation> = emptyList()
)
