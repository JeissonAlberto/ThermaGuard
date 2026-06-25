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
    private val db = ThermalDatabase.getInstance(application)
    
    private val _uiState = MutableStateFlow(ThermalUiState())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    private val _rootAvailable = MutableStateFlow(false)
    val rootAvailable: StateFlow<Boolean> = _rootAvailable.asStateFlow()

    // UI accessors
    val latest          = _uiState.map { it.latest }.stateIn(viewModelScope, SharingStarted.Eagerly, ThermalSnapshot())
    val history         = _uiState.map { it.history }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val isMonitoring    = _uiState.map { it.isMonitoring }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val isCoolingDown   = _uiState.map { it.isCoolingDown }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val profile         = _uiState.map { it.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, LearnedProfile())
    val siliconAnalysis = _uiState.map { it.siliconAnalysis }.stateIn(viewModelScope, SharingStarted.Eagerly, SiliconAnalysis())
    val coolingRecs     = _uiState.map { it.coolingRecs }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    // Config
    val appTheme       = MutableStateFlow(_prefs.getString("app_theme", "System") ?: "System")
    val userName       = MutableStateFlow(_prefs.getString("user_name", "User") ?: "User")
    val deviceNickname = MutableStateFlow(_prefs.getString("device_nickname", "Android") ?: "Android")
    val telemetryOn    = MutableStateFlow(_prefs.getBoolean("telemetry_on", true))
    val pendingUpdate  = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            try { _rootAvailable.value = RootEngine.isRootAvailable() } catch(_:Exception) {}
        }
        observeHistory()
        startLiveReading()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            db.thermalDao().getHistory(200)
                .distinctUntilChanged()
                .collect { rows ->
                    _uiState.update { it.copy(history = rows) }
                }
        }
    }

    private fun startLiveReading() {
        viewModelScope.launch {
            while (true) {
                try {
                    val snapshot = sensorRepo.readSnapshot()
                    val prof     = learningEngine.learn(snapshot)
                    val silicon  = learningEngine.analyzeSilicon(snapshot)
                    val recs     = learningEngine.generateCoolingRecommendations(snapshot, silicon, OperationMode.AUTO)
                    
                    // IA EVOLUTION - PREDICTIVE COOLING
                    val physicsParams = SiliconPhysics.detectDevicePhysicsParams()
                    val prediction = SiliconPhysics.predictFuture(snapshot, physicsParams, _uiState.value.history)
                    
                    if (prediction.expectedTemp2Min > 42f) {
                        if (_rootAvailable.value) {
                            RootEngine.setCpuMaxFreq(RootEngine.CpuLevel.THROTTLE)
                        }
                    }

                    _uiState.update { it.copy(
                        latest = snapshot,
                        profile = prof,
                        siliconAnalysis = silicon,
                        coolingRecs = recs,
                        isLoading = false
                    )}
                    
                    // Persistir snapshot periódicamente
                    db.thermalDao().insert(snapshot)

                } catch (e: Exception) {
                    android.util.Log.e("ThermaGuard", "Read error: ${e.message}")
                }
                delay(3000L)
            }
        }
    }

    fun startMonitor() { _uiState.update { it.copy(isMonitoring = true) } }
    fun setUserName(name: String) { 
        userName.value = name
        _prefs.edit().putString("user_name", name).apply()
    }
    fun setDeviceNickname(name: String) { 
        deviceNickname.value = name
        _prefs.edit().putString("device_nickname", name).apply()
    }
    fun toggleTelemetry(on: Boolean) { 
        telemetryOn.value = on 
        _prefs.edit().putBoolean("telemetry_on", on).apply()
    }
    fun setTheme(theme: String) { 
        appTheme.value = theme 
        _prefs.edit().putString("app_theme", theme).apply()
    }
    fun checkForUpdates() {}
}

data class ThermalUiState(
    val latest: ThermalSnapshot = ThermalSnapshot(),
    val profile: LearnedProfile = LearnedProfile(0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, false, TempTrend.STABLE, LearnedCause.UNKNOWN, RiskLevel.NORMAL, 0, 0f, 0f, 0f, "", 0f, 0f, 0f),
    val history: List<ThermalSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val isMonitoring: Boolean = true,
    val isCoolingDown: Boolean = false,
    val siliconAnalysis: SiliconAnalysis = SiliconAnalysis(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0, "", "", SiliconSeverity.OPTIMAL),
    val coolingRecs: List<CoolingRecommendation> = emptyList()
)
