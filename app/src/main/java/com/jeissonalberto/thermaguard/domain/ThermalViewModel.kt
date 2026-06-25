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

    // ── PROPIEDADES PARA UI (Dashboard, Stats, etc.) ──────────────────────
    val latest          = _uiState.map { it.latest }.stateIn(viewModelScope, SharingStarted.Eagerly, ThermalSnapshot())
    val history         = _uiState.map { it.history }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val profile         = _uiState.map { it.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, LearnedProfile(0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, false, TempTrend.STABLE, LearnedCause.UNKNOWN, RiskLevel.NORMAL, 0, 0f, 0f, 0f, "", 0f, 0f, 0f))
    val siliconAnalysis = _uiState.map { it.siliconAnalysis }.stateIn(viewModelScope, SharingStarted.Eagerly, SiliconAnalysis(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0, "Moore", "Optimal", SiliconSeverity.OPTIMAL))
    val coolingRecs     = _uiState.map { it.coolingRecs }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    // UI states adicionales para compatibilidad v3/v4
    val operationMode   = MutableStateFlow(OperationMode.AUTO).asStateFlow()
    val gameModeState   = MutableStateFlow(GameModeState()).asStateFlow()
    val safeChargeState = MutableStateFlow(SafeChargeState()).asStateFlow()
    val autoActionsLog  = MutableStateFlow(emptyList<AutoAction>()).asStateFlow()
    val alertThreshold  = MutableStateFlow(42f).asStateFlow()
    val causes          = MutableStateFlow(emptyList<String>()).asStateFlow()
    val prediction      = MutableStateFlow(TempPrediction(0f, PredictionConfidence.MEDIUM, "")).asStateFlow()
    val smartTips       = MutableStateFlow(emptyList<SmartTip>()).asStateFlow()
    val componentDiagnoses = MutableStateFlow(emptyList<ComponentDiagnosis>()).asStateFlow()
    val sensorLogs      = MutableStateFlow(emptyList<String>()).asStateFlow()
    val governorLog     = MutableStateFlow(emptyList<String>()).asStateFlow()
    val appHeatRanking  = MutableStateFlow(emptyList<Pair<String, Float>>()).asStateFlow()
    val hourlyProfile   = MutableStateFlow(emptyList<HourlyDataPoint>()).asStateFlow()
    val batteryHealth   = MutableStateFlow(BatteryHealthScore(100, "Good", emptyList())).asStateFlow()
    val latestSnapshot  = latest
    
    val superCoolActive = MutableStateFlow(false).asStateFlow()
    val ultraCoolActive = MutableStateFlow(false).asStateFlow()
    val superCoolResult = MutableStateFlow<String?>(null).asStateFlow()
    
    val cpuThrottled    = MutableStateFlow(false).asStateFlow()
    val gpuThrottled    = MutableStateFlow(false).asStateFlow()
    val brightnessSet   = MutableStateFlow(false).asStateFlow()
    val dataDisabled    = MutableStateFlow(false).asStateFlow()
    val appsKilled      = MutableStateFlow(0).asStateFlow()

    // Config
    val appTheme       = MutableStateFlow(_prefs.getString("app_theme", "System") ?: "System").asStateFlow()
    val appLanguage    = MutableStateFlow(_prefs.getString("app_lang", "es") ?: "es").asStateFlow()
    val userName       = MutableStateFlow(_prefs.getString("user_name", "User") ?: "User").asStateFlow()
    val deviceNickname = MutableStateFlow(_prefs.getString("device_nickname", "Android") ?: "Android").asStateFlow()
    val telemetryOn    = MutableStateFlow(_prefs.getBoolean("telemetry_on", true)).asStateFlow()
    val pendingUpdate  = MutableStateFlow<String?>(null).asStateFlow()
    
    val isMonitoring   = MutableStateFlow(true).asStateFlow()
    val isCoolingDown  = MutableStateFlow(false).asStateFlow()

    init {
        viewModelScope.launch {
            try { _rootAvailable.value = RootEngine.isRootAvailable() } catch(_:Exception) {}
        }
        observeHistory()
        startLiveReading()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            db.thermalDao().getHistory(200).distinctUntilChanged().collect { rows ->
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
                    
                    val physicsParams = SiliconPhysics.detectDevicePhysicsParams()
                    val evolutionFuture = SiliconPhysics.predictFuture(snapshot, physicsParams, _uiState.value.history)
                    
                    if (evolutionFuture.expectedTemp2Min > 42f) {
                        if (_rootAvailable.value) { RootEngine.setCpuMaxFreq(RootEngine.CpuLevel.THROTTLE) }
                    }

                    _uiState.update { it.copy(
                        latest = snapshot, profile = prof,
                        siliconAnalysis = silicon, coolingRecs = recs,
                        isLoading = false
                    )}
                    db.thermalDao().insert(snapshot)
                } catch (e: Exception) {}
                delay(3000L)
            }
        }
    }

    fun startMonitor() {}
    fun setUserName(name: String) {}
    fun setDeviceNickname(name: String) {}
    fun toggleTelemetry(on: Boolean) {}
    fun setTheme(theme: String) {}
    fun setLanguage(lang: String) {}
    fun checkForUpdates() {}
    fun activateSuperCool(ultra: Boolean = false) {}
    fun deactivateSuperCool() {}
    fun rootCpuThrottle() {}
    fun rootGpuThrottle() {}
    fun rootDisableData() {}
    fun rootSetBrightness(level: Int) {}
    fun rootKillBg() {}
}

data class ThermalUiState(
    val latest: ThermalSnapshot = ThermalSnapshot(),
    val profile: LearnedProfile = LearnedProfile(0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, false, TempTrend.STABLE, LearnedCause.UNKNOWN, RiskLevel.NORMAL, 0, 0f, 0f, 0f, "", 0f, 0f, 0f),
    val history: List<ThermalSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val isMonitoring: Boolean = true,
    val isCoolingDown: Boolean = false,
    val siliconAnalysis: SiliconAnalysis = SiliconAnalysis(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0, "Moore", "Optimal", SiliconSeverity.OPTIMAL),
    val coolingRecs: List<CoolingRecommendation> = emptyList()
)
