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

    // ── UI ACCESSORS ─────────────────────────────────────────────────────
    val latest = _uiState.map { it.latest }.stateIn(viewModelScope, SharingStarted.Eagerly, ThermalSnapshot())
    val history = _uiState.map { it.history }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val profile = _uiState.map { it.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, LearnedProfile())
    val siliconAnalysis = _uiState.map { it.siliconAnalysis }.stateIn(viewModelScope, SharingStarted.Eagerly, SiliconAnalysis())
    val coolingRecs = _uiState.map { it.coolingRecs }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    val operationMode = _uiState.map { it.operationMode }.stateIn(viewModelScope, SharingStarted.Eagerly, OperationMode.AUTO)
    val gameModeState = _uiState.map { it.gameModeState }.stateIn(viewModelScope, SharingStarted.Eagerly, GameModeState())
    val safeChargeState = _uiState.map { it.safeChargeState }.stateIn(viewModelScope, SharingStarted.Eagerly, SafeChargeState())
    val autoActionsLog = _uiState.map { it.autoActionsLog }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val causes = _uiState.map { it.causes }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val prediction = _uiState.map { it.prediction }.stateIn(viewModelScope, SharingStarted.Eagerly, TempPrediction())
    val smartTips = _uiState.map { it.smartTips }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val governorLog = _uiState.map { it.governorLog }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    val cpuThrottled = MutableStateFlow(false).asStateFlow()
    val gpuThrottled = MutableStateFlow(false).asStateFlow()
    val brightnessSet = MutableStateFlow(false).asStateFlow()
    val dataDisabled = MutableStateFlow(false).asStateFlow()
    val appsKilled = MutableStateFlow(0).asStateFlow()
    
    val alertThreshold = MutableStateFlow(42f).asStateFlow()
    val batteryHealth = MutableStateFlow(BatteryHealthScore()).asStateFlow()
    val hourlyProfile = MutableStateFlow(emptyList<HourlyDataPoint>()).asStateFlow()
    val appHeatRanking = MutableStateFlow(emptyList<Pair<String, Float>>()).asStateFlow()
    val sensorLogs = MutableStateFlow(emptyList<SensorLog>()).asStateFlow()
    val componentDiagnoses = MutableStateFlow(emptyList<ComponentDiagnosis>()).asStateFlow()
    
    val superCoolActive = MutableStateFlow(false).asStateFlow()
    val ultraCoolActive = MutableStateFlow(false).asStateFlow()
    val superCoolResult = MutableStateFlow<String?>(null).asStateFlow()

    val appTheme = MutableStateFlow(AppTheme.SYSTEM).asStateFlow()
    val appLanguage = MutableStateFlow(AppLanguage.SPANISH).asStateFlow()
    val userName = MutableStateFlow("User").asStateFlow()
    val deviceNickname = MutableStateFlow("Android").asStateFlow()
    val telemetryOn = MutableStateFlow(true).asStateFlow()
    val pendingUpdate = MutableStateFlow<String?>(null).asStateFlow()
    
    val isMonitoring = MutableStateFlow(true).asStateFlow()
    val isCoolingDown = MutableStateFlow(false).asStateFlow()
    
    val latestSnapshot = latest
    val physicsAnalysis = siliconAnalysis

    init {
        viewModelScope.launch { try { _rootAvailable.value = RootEngine.isRootAvailable() } catch(_:Exception){} }
        startLiveReading()
    }

    private fun startLiveReading() {
        viewModelScope.launch {
            while (true) {
                try {
                    val snap = sensorRepo.readSnapshot()
                    _uiState.update { it.copy(latest = snap, isLoading = false) }
                } catch (e: Exception) {}
                delay(3000L)
            }
        }
    }

    fun startMonitor() {}
    fun setUserName(name: String) {}
    fun setDeviceNickname(name: String) {}
    fun toggleTelemetry(on: Boolean) {}
    fun setTheme(theme: AppTheme) {}
    fun setLanguage(lang: AppLanguage) {}
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
    val profile: LearnedProfile = LearnedProfile(),
    val history: List<ThermalSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val siliconAnalysis: SiliconAnalysis = SiliconAnalysis(),
    val coolingRecs: List<CoolingRecommendation> = emptyList(),
    val operationMode: OperationMode = OperationMode.AUTO,
    val gameModeState: GameModeState = GameModeState(),
    val safeChargeState: SafeChargeState = SafeChargeState(),
    val autoActionsLog: List<AutoAction> = emptyList(),
    val causes: List<HeatCause> = emptyList(),
    val prediction: TempPrediction = TempPrediction(),
    val smartTips: List<SmartTip> = emptyList(),
    val governorLog: List<String> = emptyList()
)
